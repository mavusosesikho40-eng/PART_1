--[[
	CombatService
	=============
	The single place where damage and healing happen. Everything (guns, abilities, explosions,
	spawn rooms) goes through Damage/Heal so multipliers, shields, stats, ultimate charge and
	kill credit always work the same way.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Heroes = require(Shared.Heroes)
local CombatUtil = require(Shared.CombatUtil)
local Remotes = require(Shared.Remotes)
local Signal = require(Shared.Signal)

local GameState = require(script.Parent.GameState)
local ProfileService = require(script.Parent.ProfileService)
local StatusService = require(script.Parent.StatusService)
local SynergyService = require(script.Parent.SynergyService)
local DestructibleService = require(script.Parent.DestructibleService)
local FX = require(script.Parent.FX)

local CombatService = {}

CombatService.Killed = Signal.new() -- (victimProfile, killerProfile?, assistProfiles)

function CombatService.CanDamage(attacker: Model?, target: Model): boolean
	if not GameState.DamageEnabled or not CombatUtil.IsAlive(target) then
		return false
	end
	if attacker then
		if attacker == target then
			return false
		end
		if not Config.Combat.FriendlyFire and not CombatUtil.AreEnemies(attacker, target) then
			return false
		end
	end
	return true
end

function CombatService.AddUltPoints(profile, points: number)
	if points <= 0 or os.clock() < (profile.UltActiveUntil or 0) then
		return
	end
	local hero = Heroes.Get(profile.HeroId)
	if not hero then
		return
	end
	local current = ProfileService.GetUltCharge(profile)
	if current < 100 then
		ProfileService.SetUltCharge(profile, current + points / hero.UltCost * 100)
	end
end

-- Deals damage. `attacker` may be nil for environmental damage. Returns damage actually dealt.
-- info = { Ability = "Talon Rifle", Headshot = true, Position = Vector3 }
function CombatService.Damage(attacker: Model?, target: Model, amount: number, info): number
	info = info or {}
	if not CombatService.CanDamage(attacker, target) then
		return 0
	end
	local humanoid = target:FindFirstChildOfClass("Humanoid")
	if not humanoid then
		return 0
	end

	if attacker then
		amount *= StatusService.GetMultiplier(attacker, "DamageDealt")
		amount *= SynergyService.GetBonus(attacker, "DamageMult", 1)
	end
	amount *= StatusService.GetMultiplier(target, "DamageTaken")
	if amount <= 0 then
		return 0
	end

	-- Shields soak damage first.
	local shield = target:GetAttribute("Shield") or 0
	local absorbed = math.min(shield, amount)
	if absorbed > 0 then
		target:SetAttribute("Shield", shield - absorbed)
	end
	local before = humanoid.Health
	humanoid.Health = math.max(0, before - (amount - absorbed))
	local dealt = absorbed + (before - humanoid.Health)
	local killed = humanoid.Health <= 0

	local victim = ProfileService.GetByCharacter(target)
	local source = attacker and ProfileService.GetByCharacter(attacker)
	if victim and source and source ~= victim then
		victim.DamageLog[source.Id] = os.clock()
		victim.LastAttackerId = source.Id
		victim.LastAbility = info.Ability
	end

	if source then
		ProfileService.AddStat(source, "Damage", dealt)
		CombatService.AddUltPoints(source, dealt)
		if source.Player then
			local targetRoot = CombatUtil.GetRoot(target)
			Remotes.HitConfirm:FireClient(source.Player, {
				Position = info.Position or (if targetRoot then targetRoot.Position else nil),
				Amount = math.floor(dealt + 0.5),
				Headshot = info.Headshot == true,
				Kill = killed,
			})
		end
	end

	if victim and victim.Player then
		local attackerRoot = attacker and CombatUtil.GetRoot(attacker)
		Remotes.Damaged:FireClient(victim.Player, {
			From = if attackerRoot then attackerRoot.Position else nil,
			Amount = dealt,
		})
	end

	return dealt
end

-- Heals `target`. `healer` may be nil (spawn rooms). Returns health actually restored.
function CombatService.Heal(healer: Model?, target: Model, amount: number, info): number
	if not CombatUtil.IsAlive(target) then
		return 0
	end
	if healer and healer ~= target and CombatUtil.AreEnemies(healer, target) then
		return 0
	end
	if healer then
		amount *= SynergyService.GetBonus(healer, "HealingMult", 1)
	end
	local humanoid = target:FindFirstChildOfClass("Humanoid")
	local before = humanoid.Health
	humanoid.Health = math.min(humanoid.MaxHealth, before + amount)
	local healed = humanoid.Health - before

	if healed > 0 and healer then
		local source = ProfileService.GetByCharacter(healer)
		if source then
			ProfileService.AddStat(source, "Healing", healed)
			CombatService.AddUltPoints(source, healed)
			if source.Player and not (info and info.Silent) then
				local targetRoot = CombatUtil.GetRoot(target)
				Remotes.HitConfirm:FireClient(source.Player, {
					Position = if targetRoot then targetRoot.Position else nil,
					Amount = math.floor(healed + 0.5),
					Heal = true,
				})
			end
		end
	end
	return healed
end

-- Living fighters within `radius` of `center` that pass `filter`.
function CombatService.GetCharactersInRadius(center: Vector3, radius: number, filter)
	local results = {}
	for _, profile in ProfileService.All() do
		local character = profile.Character
		if character and CombatUtil.IsAlive(character) then
			local root = CombatUtil.GetRoot(character)
			if root and (root.Position - center).Magnitude <= radius then
				if not filter or filter(character) then
					table.insert(results, character)
				end
			end
		end
	end
	return results
end

function CombatService.GetEnemiesInRadius(source: Model, center: Vector3, radius: number)
	return CombatService.GetCharactersInRadius(center, radius, function(character)
		return character ~= source and CombatUtil.AreEnemies(source, character)
	end)
end

function CombatService.GetAlliesInRadius(source: Model, center: Vector3, radius: number, includeSelf: boolean?)
	local team = source:GetAttribute("Team")
	return CombatService.GetCharactersInRadius(center, radius, function(character)
		if character == source then
			return includeSelf == true
		end
		return team ~= nil and character:GetAttribute("Team") == team
	end)
end

-- True if nothing solid (walls, enemy barriers) is between two points.
function CombatService.HasLineOfSight(source: Model?, from: Vector3, to: Vector3): boolean
	local offset = to - from
	if offset.Magnitude < 0.5 then
		return true
	end
	local params
	if source then
		params = CombatUtil.ShotParams(source, true)
	else
		params = RaycastParams.new()
		params.FilterType = Enum.RaycastFilterType.Exclude
		params.FilterDescendantsInstances = CombatUtil.GetAllCharacters()
	end
	local result = workspace:Raycast(from, offset, params)
	return result == nil or (result.Position - to).Magnitude < 1.5
end

-- Area damage. Full damage at the centre, `MinFalloff` (default 50%) at the edge.
-- options = { Ability, MinFalloff, IgnoreCover, OnHit = function(character, dealt) }
function CombatService.Explode(attacker: Model, position: Vector3, radius: number, damage: number, options)
	options = options or {}
	local minFalloff = options.MinFalloff or 0.5
	local hit = {}
	for _, profile in ProfileService.All() do
		local character = profile.Character
		if character and CombatService.CanDamage(attacker, character) then
			local root = CombatUtil.GetRoot(character)
			if root then
				local distance = (root.Position - position).Magnitude
				if
					distance <= radius + 1
					and (options.IgnoreCover or CombatService.HasLineOfSight(attacker, position, root.Position))
				then
					local scale = 1 - (1 - minFalloff) * math.clamp(distance / radius, 0, 1)
					local dealt = CombatService.Damage(attacker, character, damage * scale, {
						Ability = options.Ability,
						Position = root.Position,
					})
					if dealt > 0 then
						table.insert(hit, character)
						if options.OnHit then
							options.OnHit(character, dealt)
						end
					end
				end
			end
		end
	end
	DestructibleService.DamageInRadius(position, radius, damage, attacker)
	return hit
end

-- Called when a fighter's Humanoid dies: works out the killer and assists, updates stats,
-- posts to the kill feed and tells the match to start the respawn timer.
function CombatService.HandleDeath(character: Model)
	local victim = ProfileService.GetByCharacter(character)
	if not victim or victim.Character ~= character or victim.DeathHandledFor == character then
		return
	end
	victim.DeathHandledFor = character

	local now = os.clock()
	local window = Config.Match.AssistWindow
	local killer = nil
	local lastId = victim.LastAttackerId
	if lastId and victim.DamageLog[lastId] and now - victim.DamageLog[lastId] <= window then
		killer = ProfileService.Get(lastId)
	end
	local assists = {}
	for attackerId, time in victim.DamageLog do
		if attackerId ~= lastId and now - time <= window then
			local assister = ProfileService.Get(attackerId)
			if assister then
				table.insert(assists, assister)
			end
		end
	end
	victim.DamageLog = {}
	victim.LastAttackerId = nil

	ProfileService.AddStat(victim, "Deaths", 1)
	if killer then
		ProfileService.AddStat(killer, "Kills", 1)
	end
	for _, assister in assists do
		ProfileService.AddStat(assister, "Assists", 1)
	end

	FX.Notify("Kill", {
		KillerName = if killer then killer.Name else nil,
		KillerHero = if killer then killer.HeroId else nil,
		KillerTeam = if killer then killer.Team else nil,
		VictimName = victim.Name,
		VictimHero = victim.HeroId,
		VictimTeam = victim.Team,
		Ability = if killer then victim.LastAbility else nil,
	})
	FX.NotifyPlayer(victim.Player, "Eliminated", {
		By = if killer then killer.Name else nil,
		ByHero = if killer then killer.HeroId else nil,
	})
	if killer then
		FX.NotifyPlayer(killer.Player, "KillConfirmed", { Victim = victim.Name })
	end

	CombatService.Killed:Fire(victim, killer, assists)
end

return CombatService
