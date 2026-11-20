--[[
	MatchService
	============
	Runs the match loop and everything around it:
	  * players joining/leaving, team balance, filling empty slots with bots
	  * hero selection and swapping
	  * respawns, spawn-room healing, falling off the map
	  * the phases: Waiting -> Intermission -> (Setup -> Battle -> RoundEnd) x N -> MatchEnd
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local Teams = game:GetService("Teams")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Heroes = require(Shared.Heroes)
local CombatUtil = require(Shared.CombatUtil)
local Remotes = require(Shared.Remotes)

local GameState = require(script.Parent.GameState)
local ProfileService = require(script.Parent.ProfileService)
local CombatService = require(script.Parent.CombatService)
local HeroService = require(script.Parent.HeroService)
local BotService = require(script.Parent.BotService)
local ObjectiveService = require(script.Parent.ObjectiveService)
local DestructibleService = require(script.Parent.DestructibleService)
local MapService = require(script.Parent.MapService)
local FX = require(script.Parent.FX)

local MatchService = {}

local TEAM_A, TEAM_B = Config.Teams[1].Name, Config.Teams[2].Name
local teamObjects = {}
local rng = Random.new()

--------------------------------------------------------------------------------
-- Teams and bots
--------------------------------------------------------------------------------

local function humans()
	local list = {}
	for _, profile in ProfileService.All() do
		if profile.Player then
			table.insert(list, profile)
		end
	end
	return list
end

local function countOnTeam(team: string, wantBots: boolean?)
	local count = 0
	for _, profile in ProfileService.OnTeam(team) do
		if wantBots == nil or profile.IsBot == wantBots then
			count += 1
		end
	end
	return count
end

local function setTeam(profile, team: string)
	ProfileService.SetTeam(profile, team)
	if profile.Player then
		profile.Player.Team = teamObjects[team]
		profile.Player.Neutral = false
	end
	if profile.Character then
		profile.Character:SetAttribute("Team", team)
	end
end

local function pickTeamForNewPlayer(): string
	local a, b = countOnTeam(TEAM_A, false), countOnTeam(TEAM_B, false)
	if a ~= b then
		return if a < b then TEAM_A else TEAM_B
	end
	return if rng:NextNumber() < 0.5 then TEAM_A else TEAM_B
end

local function matchRunning(): boolean
	local phase = GameState.Phase
	return phase == "Setup" or phase == "Battle" or phase == "RoundEnd"
end

local function nextBotName(): string
	local used = {}
	for _, profile in ProfileService.All() do
		used[profile.Name] = true
	end
	for _, name in Config.Bots.Names do
		local botName = name .. " (Bot)"
		if not used[botName] then
			return botName
		end
	end
	return "Bot " .. rng:NextInteger(100, 999)
end

-- Adds or removes bots so each team has Config.Match.BotsFillTeamsTo fighters.
function MatchService.RebalanceBots()
	local anyHumans = #humans() > 0
	for _, team in { TEAM_A, TEAM_B } do
		local wanted = if anyHumans then math.max(0, Config.Match.BotsFillTeamsTo - countOnTeam(team, false)) else 0
		local bots = {}
		for _, profile in ProfileService.OnTeam(team) do
			if profile.IsBot then
				table.insert(bots, profile)
			end
		end
		while #bots > wanted do
			ProfileService.Remove(table.remove(bots))
		end
		while #bots < wanted do
			local bot = ProfileService.AddBot(nextBotName())
			setTeam(bot, team)
			table.insert(bots, bot)
			if matchRunning() then
				ProfileService.SetHero(bot, BotService.ChooseHero(bot))
				task.spawn(HeroService.Spawn, bot)
			end
		end
	end
end

-- Evens out the human players between matches.
local function balanceHumans()
	local a, b = {}, {}
	for _, profile in humans() do
		table.insert(if profile.Team == TEAM_A then a else b, profile)
	end
	while #a - #b > 1 do
		local moved = table.remove(a, rng:NextInteger(1, #a))
		setTeam(moved, TEAM_B)
		table.insert(b, moved)
	end
	while #b - #a > 1 do
		local moved = table.remove(b, rng:NextInteger(1, #b))
		setTeam(moved, TEAM_A)
		table.insert(a, moved)
	end
end

--------------------------------------------------------------------------------
-- Players
--------------------------------------------------------------------------------

local function onPlayerAdded(player: Player)
	player.DevEnableMouseLock = false -- Shift is an ability key
	local profile = ProfileService.AddPlayer(player)
	setTeam(profile, pickTeamForNewPlayer())
	MatchService.RebalanceBots()
end

local function onPlayerRemoving(player: Player)
	local profile = ProfileService.GetByPlayer(player)
	if profile then
		ProfileService.Remove(profile)
	end
	MatchService.RebalanceBots()
end

local function teammateHasHero(profile, heroId: string): boolean
	for _, mate in ProfileService.OnTeam(profile.Team) do
		if mate ~= profile and mate.HeroId == heroId then
			return true
		end
	end
	return false
end

local function onSelectHero(player: Player, heroId)
	local hero = Heroes.Get(heroId)
	local profile = ProfileService.GetByPlayer(player)
	if not hero or not profile or not profile.Team then
		return
	end
	if not Config.Match.AllowDuplicateHeroes and teammateHasHero(profile, heroId) then
		FX.NotifyPlayer(player, "Message", { Text = hero.DisplayName .. " is already on your team." })
		return
	end

	local character = profile.Character
	local alive = CombatUtil.IsAlive(character)
	local root = CombatUtil.GetRoot(character)
	local inSpawn = root ~= nil and MapService.IsInSpawnRoom(root.Position, profile.Team)
	if alive and GameState.Phase ~= "Setup" and not inSpawn then
		FX.NotifyPlayer(player, "Message", { Text = "Return to your spawn room to change heroes." })
		return
	end

	if profile.HeroId ~= heroId then
		ProfileService.SetHero(profile, heroId)
		ProfileService.SetUltCharge(profile, 0)
	end
	-- Swap right away if we're alive in spawn or haven't spawned yet; dead players use it on respawn.
	if matchRunning() and (alive or not character) then
		task.spawn(HeroService.Spawn, profile)
	end
end

--------------------------------------------------------------------------------
-- Continuous upkeep: respawns, spawn rooms, falling, passive ult charge
--------------------------------------------------------------------------------

local accumulator = 0
local function upkeep(dt: number)
	accumulator += dt
	if accumulator < 0.25 then
		return
	end
	local step = accumulator
	accumulator = 0
	local phase = GameState.Phase
	local battle = phase == "Battle"

	for _, profile in ProfileService.All() do
		local character = profile.Character
		if character and CombatUtil.IsAlive(character) then
			local root = CombatUtil.GetRoot(character)
			if root then
				if root.Position.Y < Config.Match.KillHeight then
					character:FindFirstChildOfClass("Humanoid").Health = 0
				elseif MapService.IsInSpawnRoom(root.Position, profile.Team) then
					CombatService.Heal(nil, character, Config.Match.SpawnRoomHealPerSecond * step)
				elseif battle and MapService.IsInSpawnRoom(root.Position, Config.GetOtherTeam(profile.Team)) then
					CombatService.Damage(nil, character, Config.Match.EnemySpawnRoomDamagePerSecond * step)
				end
			end
			if battle and os.clock() >= profile.UltActiveUntil then
				local charge = ProfileService.GetUltCharge(profile)
				if charge < 100 then
					ProfileService.SetUltCharge(profile, charge + Config.Combat.PassiveUltChargePerSecond * step)
				end
			end
		elseif profile.RespawnAt and (phase == "Setup" or battle) and os.clock() >= profile.RespawnAt then
			profile.RespawnAt = nil
			task.spawn(HeroService.Spawn, profile)
		end
	end
end

local function onKilled(victim)
	victim.Folder:SetAttribute("Alive", false)
	victim.RespawnAt = os.clock() + Config.Match.RespawnTime
	victim.Folder:SetAttribute("RespawnAt", workspace:GetServerTimeNow() + Config.Match.RespawnTime)
end

--------------------------------------------------------------------------------
-- Phases
--------------------------------------------------------------------------------

local function waitSeconds(seconds: number)
	local finish = os.clock() + seconds
	while os.clock() < finish do
		task.wait(0.2)
	end
end

local function resetForNewMatch()
	for _, profile in ProfileService.All() do
		HeroService.Despawn(profile)
		ProfileService.SetHero(profile, nil)
		ProfileService.SetUltCharge(profile, 0)
		ProfileService.ResetStats(profile)
	end
	ObjectiveService.Reset()
	DestructibleService.ResetAll()
	MapService.ClearEffects()
	MapService.SetDoorsOpen(false)
	GameState.Set("Round", 0)
	GameState.Set("RoundWins_" .. TEAM_A, 0)
	GameState.Set("RoundWins_" .. TEAM_B, 0)
end

local function playRound(round: number): string?
	-- SETUP: everyone (re)spawns in their spawn room and picks heroes. Doors are shut.
	ObjectiveService.Reset()
	DestructibleService.ResetAll()
	MapService.ClearEffects()
	MapService.SetDoorsOpen(false)
	GameState.Set("Round", round)
	GameState.SetPhase("Setup", Config.Match.SetupTime)
	FX.Notify("Announce", {
		Title = "ROUND " .. round,
		Subtitle = if round == 1 then "Choose your hero" else "Press " .. Config.KeyLabels.HeroSelect .. " to change hero",
		Duration = 3,
	})

	MatchService.RebalanceBots()
	for _, profile in ProfileService.All() do
		if profile.IsBot and not profile.HeroId then
			ProfileService.SetHero(profile, BotService.ChooseHero(profile))
		end
		profile.RespawnAt = nil
		if profile.HeroId then
			task.spawn(HeroService.Spawn, profile)
		end
	end
	waitSeconds(Config.Match.SetupTime)

	-- BATTLE: doors open, the point unlocks shortly after.
	MapService.SetDoorsOpen(true)
	ObjectiveService.Start(Config.Match.PointUnlockDelay)
	GameState.SetPhase("Battle", nil)
	FX.Notify("Announce", {
		Title = "FIGHT!",
		Subtitle = "The point unlocks in " .. Config.Match.PointUnlockDelay .. " seconds",
		Duration = 2.5,
	})

	local winner = nil
	while not winner do
		local dt = task.wait(0.1)
		winner = ObjectiveService.Update(dt)
		if #humans() == 0 then
			return nil
		end
	end

	-- ROUND END
	GameState.SetPhase("RoundEnd", Config.Match.RoundEndTime)
	GameState.Set("LastRoundWinner", winner)
	FX.Notify("RoundOver", { Winner = winner, Round = round })
	waitSeconds(Config.Match.RoundEndTime)
	return winner
end

local function runMatch(): string?
	local wins = { [TEAM_A] = 0, [TEAM_B] = 0 }
	local round = 0
	while true do
		round += 1
		local winner = playRound(round)
		if not winner then
			return nil -- everyone left
		end
		wins[winner] += 1
		GameState.Set("RoundWins_" .. winner, wins[winner])
		if wins[winner] >= Config.Match.RoundsToWin then
			return winner
		end
	end
end

local function mainLoop()
	while true do
		resetForNewMatch()
		GameState.SetPhase("Waiting", nil)
		while #humans() < Config.Match.MinPlayersToStart do
			task.wait(1)
		end

		GameState.SetPhase("Intermission", Config.Match.IntermissionTime)
		waitSeconds(Config.Match.IntermissionTime)
		if #humans() >= Config.Match.MinPlayersToStart then
			balanceHumans()
			local winner = runMatch()
			if winner then
				GameState.SetPhase("MatchEnd", Config.Match.MatchEndTime)
				GameState.Set("MatchWinner", winner)
				FX.Notify("MatchOver", { Winner = winner })
				waitSeconds(Config.Match.MatchEndTime)
			end
		end
	end
end

function MatchService.Start()
	for _, def in Config.Teams do
		local team = Teams:FindFirstChild(def.Name)
		if not team then
			team = Instance.new("Team")
			team.Name = def.Name
			team.Parent = Teams
		end
		team.TeamColor = def.BrickColor
		team.AutoAssignable = false
		teamObjects[def.Name] = team
	end

	Players.PlayerAdded:Connect(onPlayerAdded)
	Players.PlayerRemoving:Connect(onPlayerRemoving)
	for _, player in Players:GetPlayers() do
		task.spawn(onPlayerAdded, player)
	end

	Remotes.SelectHero.OnServerEvent:Connect(onSelectHero)
	CombatService.Killed:Connect(onKilled)
	RunService.Heartbeat:Connect(upkeep)
	task.spawn(mainLoop)
end

return MatchService
