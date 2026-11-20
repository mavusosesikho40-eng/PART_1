--[[
	Bulwark (Tank) — ability code. Numbers live in Shared/Heroes.luau.
	  Ability1  Ram Dash           dash that damages and knocks back enemies
	  Ability2  Aegis Wall         placeable energy barrier with health
	  Ultimate  Fortress Protocol  team-wide shields and damage reduction
]]

local CollectionService = game:GetService("CollectionService")
local Debris = game:GetService("Debris")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local CombatUtil = require(Shared.CombatUtil)

local Services = script.Parent.Parent:WaitForChild("Services")
local CombatService = require(Services.CombatService)
local StatusService = require(Services.StatusService)
local MotionService = require(Services.MotionService)
local SynergyService = require(Services.SynergyService)
local MapService = require(Services.MapService)
local FX = require(Services.FX)
local KitUtil = require(script.Parent.KitUtil)

local Kit = {}

local walls = setmetatable({}, { __mode = "k" }) -- [character] = their current wall (one at a time)

-- Ram Dash: charge forward; every enemy we pass through is hit once and shoved away.
function Kit.Ability1(ctx)
	local ability = ctx.Ability
	local character = ctx.Character
	local direction = CombatUtil.Flatten(ctx.AimDirection)
	MotionService.Dash(character, direction * (ability.Distance / ability.Duration), ability.Duration)
	FX.All("DashTrail", { Character = character, Duration = ability.Duration, Color = ctx.Hero.AccentColor })

	local alreadyHit = {}
	local started = os.clock()
	local connection
	connection = RunService.Heartbeat:Connect(function()
		if os.clock() - started > ability.Duration + 0.15 or not CombatUtil.IsAlive(character) then
			connection:Disconnect()
			return
		end
		for _, enemy in CombatService.GetEnemiesInRadius(character, ctx.Root.Position, ability.HitRadius) do
			if not alreadyHit[enemy] then
				alreadyHit[enemy] = true
				CombatService.Damage(character, enemy, ability.Damage, { Ability = ability.Name })
				MotionService.Knockback(enemy, direction * ability.Knockback + Vector3.new(0, 25, 0))
			end
		end
	end)
end

-- Aegis Wall: a barrier that blocks enemy shots. It is a "Destructible" with a Team, so enemies
-- can shoot it down while allies (see CombatUtil.ShotParams) shoot straight through it.
function Kit.Ability2(ctx)
	local ability = ctx.Ability
	local direction = CombatUtil.Flatten(ctx.AimDirection)
	local center = KitUtil.GroundAt(ctx.Root.Position + direction * ability.PlaceDistance)
	local health = ability.Health * SynergyService.GetBonus(ctx.Character, "BarrierHealthMult", 1)

	local previous = walls[ctx.Character]
	if previous and previous.Parent then
		previous:Destroy()
	end

	local wall = Instance.new("Part")
	wall.Name = "AegisWall"
	wall.Size = Vector3.new(ability.Width, ability.Height, 0.6)
	wall.CFrame = CFrame.lookAt(center, center + direction) + Vector3.new(0, ability.Height / 2, 0)
	wall.Anchored = true
	wall.CanCollide = false -- heroes walk through it, bullets do not
	wall.CanTouch = false
	wall.CastShadow = false
	wall.Material = Enum.Material.ForceField
	wall.Color = ctx.Hero.AccentColor
	wall.Transparency = 0.1
	wall:SetAttribute("Team", ctx.Team)
	wall:SetAttribute("MaxHealth", health)
	wall:SetAttribute("Permanent", true)
	CollectionService:AddTag(wall, "Barrier")
	CollectionService:AddTag(wall, "Destructible")
	wall.Parent = MapService.GetEffectsFolder()
	walls[ctx.Character] = wall
	Debris:AddItem(wall, ability.Duration)

	FX.All("Ring", { Position = center + Vector3.new(0, 0.5, 0), Radius = ability.Width / 2, Color = ctx.Hero.AccentColor })
end

-- Fortress Protocol: shields + damage reduction for everyone nearby on our team.
function Kit.Ultimate(ctx)
	local ability = ctx.Ability
	local color = ctx.Hero.AccentColor
	for _, ally in CombatService.GetAlliesInRadius(ctx.Character, ctx.Root.Position, ability.Radius, true) do
		StatusService.AddShield(ally, ability.Shield, ability.Duration)
		StatusService.Apply(ally, "Fortress", { DamageTaken = 1 - ability.DamageReduction }, ability.Duration)
		FX.All("Aura", { Character = ally, Duration = ability.Duration, Color = color })
	end
	FX.All("Ring", { Position = ctx.Root.Position, Radius = ability.Radius, Color = color, Time = 0.6 })
end

return Kit
