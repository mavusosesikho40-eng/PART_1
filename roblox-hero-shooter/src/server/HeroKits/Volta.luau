--[[
	Volta (Damage) — ability code. Numbers live in Shared/Heroes.luau.
	  Ability1  Blink         short-range teleport (stops before walls)
	  Ability2  Static Field  damaging, slowing area on the ground
	  Ultimate  Thunderhead   three lightning strikes after a short warning
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local CombatUtil = require(Shared.CombatUtil)

local Services = script.Parent.Parent:WaitForChild("Services")
local CombatService = require(Services.CombatService)
local StatusService = require(Services.StatusService)
local MotionService = require(Services.MotionService)
local FX = require(Services.FX)
local KitUtil = require(script.Parent.KitUtil)

local Kit = {}

-- Blink: check for walls at foot, waist and head height and stop just short of the nearest one.
-- (Raycasts, not a sphere cast: a sphere that starts inside a wall you're hugging would miss it.)
function Kit.Ability1(ctx)
	local ability = ctx.Ability
	local direction = CombatUtil.Flatten(ctx.AimDirection)
	local start = ctx.Root.Position
	local params = KitUtil.WorldParams()
	local distance = ability.Distance
	for _, height in { -1.8, 0, 1.5 } do
		local result = workspace:Raycast(start + Vector3.new(0, height, 0), direction * ability.Distance, params)
		if result then
			distance = math.min(distance, math.max(0, result.Distance - 2))
		end
	end
	local destination = start + direction * distance

	FX.All("Blink", { From = start, To = destination, Color = ctx.Hero.AccentColor })
	MotionService.Teleport(ctx.Character, CFrame.lookAt(destination, destination + direction))
end

-- Static Field: placed where you aim (up to MaxRange away), ticks damage and slow.
function Kit.Ability2(ctx)
	local ability = ctx.Ability
	local character = ctx.Character
	local point = KitUtil.GroundAt(KitUtil.ClampRange(ctx.Root.Position, ctx.AimPoint, ability.MaxRange))
	KitUtil.MakeZone(point, ability.Radius, ctx.Hero.AccentColor, ability.Duration, 0.6)

	KitUtil.Repeat(ability.Duration, ability.TickInterval, function()
		for _, enemy in CombatService.GetEnemiesInRadius(character, point, ability.Radius) do
			CombatService.Damage(character, enemy, ability.TickDamage, { Ability = ability.Name })
			StatusService.Apply(enemy, "Static", { Speed = 1 - ability.SlowAmount }, ability.TickInterval + 0.2)
		end
	end)
end

-- Thunderhead: a warning circle, then several strikes that ignore cover (they come from above).
function Kit.Ultimate(ctx)
	local ability = ctx.Ability
	local character = ctx.Character
	local color = ctx.Hero.AccentColor
	local point = KitUtil.GroundAt(KitUtil.ClampRange(ctx.Root.Position, ctx.AimPoint, ability.MaxRange))

	FX.All("Telegraph", { Position = point, Radius = ability.Radius, Duration = ability.Duration, Color = color })
	task.delay(ability.Delay, function()
		for _ = 1, ability.Strikes do
			FX.All("Lightning", { Position = point, Radius = ability.Radius, Color = color })
			CombatService.Explode(character, point + Vector3.new(0, 3, 0), ability.Radius, ability.StrikeDamage, {
				Ability = ability.Name,
				MinFalloff = 0.7,
				IgnoreCover = true,
			})
			task.wait(ability.StrikeInterval)
		end
	end)
end

return Kit
