--[[
	ProfileService
	==============
	A "profile" is one fighter in the match: a real player or a bot. It survives deaths and
	respawns, and remembers the team, chosen hero, ultimate charge and stats.

	Each profile also has a Folder in ReplicatedStorage.Combatants whose attributes mirror the
	public parts (name, team, hero, kills, ...) so clients can draw the scoreboard and HUD.
	A character points back at its profile with the "CombatantId" attribute.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Signal = require(Shared.Signal)

local ProfileService = {}

ProfileService.Changed = Signal.new() -- (profile, field) when team or hero changes
ProfileService.Removed = Signal.new() -- (profile)

local STATS = { "Kills", "Deaths", "Assists", "Damage", "Healing" }
ProfileService.Stats = STATS

local profiles = {}
local byPlayer = {}
local botCounter = 0

local root = ReplicatedStorage:FindFirstChild("Combatants")
if not root then
	root = Instance.new("Folder")
	root.Name = "Combatants"
	root.Parent = ReplicatedStorage
end

local function create(id: string, name: string, player: Player?)
	local folder = Instance.new("Folder")
	folder.Name = id
	folder:SetAttribute("DisplayName", name)
	folder:SetAttribute("IsBot", player == nil)
	folder:SetAttribute("UserId", if player then player.UserId else 0)
	folder:SetAttribute("Team", "")
	folder:SetAttribute("Hero", "")
	folder:SetAttribute("UltCharge", 0)
	folder:SetAttribute("Alive", false)
	folder:SetAttribute("RespawnAt", 0)
	folder:SetAttribute("Synergies", "")
	for _, stat in STATS do
		folder:SetAttribute(stat, 0)
	end
	folder.Parent = root

	local profile = {
		Id = id,
		Name = name,
		Player = player,
		IsBot = player == nil,
		Team = nil,
		HeroId = nil,
		Character = nil,
		Folder = folder,
		DamageLog = {}, -- [attackerProfileId] = os.clock() of their last hit
		LastAttackerId = nil,
		LastAbility = nil,
		UltActiveUntil = 0,
		RespawnAt = nil,
		Spawning = false,
	}
	profiles[id] = profile
	return profile
end

function ProfileService.AddPlayer(player: Player)
	local profile = create("P" .. player.UserId, player.DisplayName, player)
	byPlayer[player] = profile
	return profile
end

function ProfileService.AddBot(name: string)
	botCounter += 1
	return create("B" .. botCounter, name, nil)
end

function ProfileService.Remove(profile)
	if profiles[profile.Id] ~= profile then
		return
	end
	profiles[profile.Id] = nil
	if profile.Player then
		byPlayer[profile.Player] = nil
	elseif profile.Character then
		profile.Character:Destroy()
	end
	profile.Character = nil
	profile.Folder:Destroy()
	ProfileService.Removed:Fire(profile)
end

function ProfileService.Get(id)
	return if id then profiles[id] else nil
end

function ProfileService.GetByPlayer(player: Player)
	return byPlayer[player]
end

function ProfileService.GetByCharacter(character)
	if not character then
		return nil
	end
	local id = character:GetAttribute("CombatantId")
	return if id then profiles[id] else nil
end

function ProfileService.All()
	local list = {}
	for _, profile in profiles do
		table.insert(list, profile)
	end
	return list
end

function ProfileService.OnTeam(team: string)
	local list = {}
	for _, profile in profiles do
		if profile.Team == team then
			table.insert(list, profile)
		end
	end
	return list
end

function ProfileService.SetTeam(profile, team: string?)
	profile.Team = team
	profile.Folder:SetAttribute("Team", team or "")
	ProfileService.Changed:Fire(profile, "Team")
end

function ProfileService.SetHero(profile, heroId: string?)
	profile.HeroId = heroId
	profile.Folder:SetAttribute("Hero", heroId or "")
	ProfileService.Changed:Fire(profile, "Hero")
end

function ProfileService.GetUltCharge(profile): number
	return profile.Folder:GetAttribute("UltCharge") or 0
end

function ProfileService.SetUltCharge(profile, percent: number)
	profile.Folder:SetAttribute("UltCharge", math.clamp(percent, 0, 100))
end

function ProfileService.AddStat(profile, stat: string, amount: number)
	local folder = profile.Folder
	folder:SetAttribute(stat, (folder:GetAttribute(stat) or 0) + amount)
end

function ProfileService.ResetStats(profile)
	for _, stat in STATS do
		profile.Folder:SetAttribute(stat, 0)
	end
	profile.DamageLog = {}
	profile.LastAttackerId = nil
end

return ProfileService
