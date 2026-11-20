--[[
	Lumen (Support) — ability code. Numbers live in Shared/Heroes.luau.
	  Ability1  Radiant Pulse  instant area heal
	  Ability2  Tailwind       area speed boost
	  Ultimate  Sanctuary      healing zone that also reduces damage taken
]]

local Services = script.Parent.Parent:WaitForChild("Services")
local CombatService = require(Services.CombatService)
local StatusService = require(Services.StatusService)
local FX = require(Services.FX)
local KitUtil = require(script.Parent.KitUtil)

local TAILWIND_COLOR = Color3.fromRGB(150, 255, 200)

local Kit = {}

-- Radiant Pulse
function Kit.Ability1(ctx)
	local ability = ctx.Ability
	for _, ally in CombatService.GetAlliesInRadius(ctx.Character, ctx.Root.Position, ability.Radius, true) do
		CombatService.Heal(ctx.Character, ally, ability.Heal, { Ability = ability.Name })
	end
	FX.All("Ring", { Position = ctx.Root.Position, Radius = ability.Radius, Color = ctx.Hero.AccentColor, Time = 0.5 })
end

-- Tailwind
function Kit.Ability2(ctx)
	local ability = ctx.Ability
	for _, ally in CombatService.GetAlliesInRadius(ctx.Character, ctx.Root.Position, ability.Radius, true) do
		StatusService.Apply(ally, "Tailwind", { Speed = 1 + ability.SpeedBoost }, ability.Duration)
		FX.All("DashTrail", { Character = ally, Duration = ability.Duration, Color = TAILWIND_COLOR })
	end
	FX.All("Ring", { Position = ctx.Root.Position, Radius = ability.Radius, Color = TAILWIND_COLOR, Time = 0.5 })
end

-- Sanctuary: stays where Lumen stood, even if Lumen moves away or falls.
function Kit.Ultimate(ctx)
	local ability = ctx.Ability
	local character = ctx.Character
	local point = KitUtil.GroundAt(ctx.Root.Position)
	local interval = 0.25
	KitUtil.MakeZone(point, ability.Radius, ctx.Hero.AccentColor, ability.Duration, 0.55)

	KitUtil.Repeat(ability.Duration, interval, function()
		for _, ally in CombatService.GetAlliesInRadius(character, point, ability.Radius, true) do
			CombatService.Heal(character, ally, ability.HealPerSecond * interval, { Ability = ability.Name, Silent = true })
			StatusService.Apply(ally, "Sanctuary", { DamageTaken = 1 - ability.DamageReduction }, interval + 0.15)
		end
	end)
end

return Kit
