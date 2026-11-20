--[[
	Kaida (Damage) — ability code. Numbers live in Shared/Heroes.luau.
	  Ability1  Phantom Dash   damaging dash; any elimination resets its cooldown
	  Ability2  Guard Stance   brief damage reduction, then a slash all around
	  Ultimate  Blade Tempest  spinning blade storm that damages everything nearby
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local CombatUtil = require(Shared.CombatUtil)

local Services = script.Parent.Parent:WaitForChild("Services")
local CombatService = require(Services.CombatService)
local StatusService = require(Services.StatusService)
local MotionService = require(Services.MotionService)
local FX = require(Services.FX)
local KitUtil = require(script.Parent.KitUtil)

local Kit = {}

-- Passive: every elimination Kaida gets refreshes Phantom Dash.
CombatService.Killed:Connect(function(_victim, killer)
	if killer and killer.HeroId == "Kaida" and killer.Character then
		killer.Character:SetAttribute("CD_Ability1", 0)
	end
end)

-- Phantom Dash
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
			end
		end
	end)
end

-- Guard Stance
function Kit.Ability2(ctx)
	local ability = ctx.Ability
	local character = ctx.Character
	local color = ctx.Hero.AccentColor
	StatusService.Apply(character, "Guard Stance", { DamageTaken = 1 - ability.DamageReduction }, ability.Duration)
	FX.All("Aura", { Character = character, Duration = ability.Duration, Color = color })

	task.delay(ability.Duration, function()
		local root = CombatUtil.GetRoot(character)
		if not root or not CombatUtil.IsAlive(character) then
			return
		end
		for _, enemy in CombatService.GetEnemiesInRadius(character, root.Position, ability.CounterRadius) do
			CombatService.Damage(character, enemy, ability.CounterDamage, { Ability = ability.Name })
		end
		FX.All("Ring", { Position = root.Position, Radius = ability.CounterRadius, Color = color, Time = 0.3 })
	end)
end

-- Blade Tempest
function Kit.Ultimate(ctx)
	local ability = ctx.Ability
	local character = ctx.Character
	local color = ctx.Hero.AccentColor
	StatusService.Apply(character, "Blade Tempest", { Speed = 1 + ability.SpeedBoost }, ability.Duration)
	FX.All("Aura", { Character = character, Duration = ability.Duration, Color = color, Radius = ability.Radius })

	local ticks = 0
	KitUtil.Repeat(ability.Duration, ability.TickInterval, function()
		local root = CombatUtil.GetRoot(character)
		if not root then
			return
		end
		for _, enemy in CombatService.GetEnemiesInRadius(character, root.Position, ability.Radius) do
			CombatService.Damage(character, enemy, ability.TickDamage, { Ability = ability.Name })
		end
		ticks += 1
		if ticks % 2 == 0 then
			FX.All("Ring", { Position = root.Position, Radius = ability.Radius, Color = color, Time = 0.25 })
		end
	end, function()
		return CombatUtil.IsAlive(character)
	end)
end

return Kit
