--[[
	AbilityService
	==============
	Receives ability requests (from players and bots), checks cooldowns / ultimate charge, then runs
	the hero's kit function from ServerScriptService/Server/HeroKits/<HeroId>.luau.

	A kit function receives a context table:
		ctx.Character, ctx.Humanoid, ctx.Root, ctx.Team, ctx.Profile, ctx.Player (nil for bots)
		ctx.Hero        hero definition from Shared/Heroes
		ctx.Ability     this ability's definition (all the numbers)
		ctx.AimOrigin   where shots come from
		ctx.AimDirection unit vector toward what the player is aiming at
		ctx.AimPoint    the world position under the crosshair
	Return false to cancel (no cooldown is spent); anything else counts as used.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Heroes = require(Shared.Heroes)
local CombatUtil = require(Shared.CombatUtil)
local Remotes = require(Shared.Remotes)

local GameState = require(script.Parent.GameState)
local ProfileService = require(script.Parent.ProfileService)
local SynergyService = require(script.Parent.SynergyService)
local FX = require(script.Parent.FX)

local KitsFolder = script.Parent.Parent:WaitForChild("HeroKits")

local AbilityService = {}

local SLOTS = { Ability1 = true, Ability2 = true, Ultimate = true }
local kits = {}

-- Never trust aim data from a client: fix up anything missing, broken or absurd.
local function sanitizeAim(character: Model, aim)
	local origin = CombatUtil.GetMuzzle(character) or Vector3.zero
	local root = CombatUtil.GetRoot(character)
	local fallback = if root then root.CFrame.LookVector else Vector3.new(0, 0, -1)

	local direction = if typeof(aim) == "table" then aim.Direction else nil
	if not CombatUtil.IsFiniteVector(direction) or direction.Magnitude < 0.5 then
		direction = fallback
	end
	direction = direction.Unit

	local point = if typeof(aim) == "table" then aim.Point else nil
	if not CombatUtil.IsFiniteVector(point) or (point - origin).Magnitude > 600 then
		point = origin + direction * 100
	end
	return { Origin = origin, Direction = direction, Point = point }
end

-- Returns true if the ability went off.
function AbilityService.TryUse(character: Model, slot: string, aim): boolean
	if not SLOTS[slot] or not CombatUtil.IsAlive(character) then
		return false
	end
	if GameState.Phase == "MatchEnd" or GameState.Phase == "Intermission" then
		return false
	end
	local profile = ProfileService.GetByCharacter(character)
	local heroId = character:GetAttribute("Hero")
	local hero = Heroes.Get(heroId)
	local kit = kits[heroId]
	if not profile or profile.Character ~= character or not hero or not kit or not kit[slot] then
		return false
	end
	local ability = hero.Abilities[slot]

	local now = workspace:GetServerTimeNow()
	if slot == "Ultimate" then
		if ProfileService.GetUltCharge(profile) < 100 then
			return false
		end
	elseif now < (character:GetAttribute("CD_" .. slot) or 0) - 0.05 then
		return false
	end

	aim = sanitizeAim(character, aim)
	local context = {
		Character = character,
		Humanoid = character:FindFirstChildOfClass("Humanoid"),
		Root = CombatUtil.GetRoot(character),
		Team = character:GetAttribute("Team"),
		Profile = profile,
		Player = profile.Player,
		Hero = hero,
		Ability = ability,
		AimOrigin = aim.Origin,
		AimDirection = aim.Direction,
		AimPoint = aim.Point,
	}

	local ok, result = pcall(kit[slot], context)
	if not ok then
		warn(string.format("[AbilityService] %s %s failed: %s", heroId, slot, tostring(result)))
		return false
	end
	if result == false then
		return false
	end

	if slot == "Ultimate" then
		ProfileService.SetUltCharge(profile, 0)
		profile.UltActiveUntil = os.clock() + (ability.Duration or 0)
		FX.All("UltActivated", { Hero = hero.Id, Team = profile.Team, Name = profile.Name, Ability = ability.Name })
	else
		local cooldown = ability.Cooldown * SynergyService.GetBonus(profile, "CooldownMult", 1)
		character:SetAttribute("CD_" .. slot, now + cooldown)
	end
	return true
end

function AbilityService.Init()
	for _, module in KitsFolder:GetChildren() do
		if module:IsA("ModuleScript") and Heroes.Get(module.Name) then
			kits[module.Name] = require(module)
		end
	end
	for _, heroId in Heroes.Order do
		if not kits[heroId] then
			warn("[AbilityService] No kit found for hero '" .. heroId .. "' (HeroKits/" .. heroId .. ")")
		end
	end

	Remotes.UseAbility.OnServerEvent:Connect(function(player, slot, aim)
		local character = player.Character
		if typeof(slot) == "string" and character then
			AbilityService.TryUse(character, slot, aim)
		end
	end)
end

return AbilityService
