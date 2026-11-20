--[[
	Kestrel (Damage) — ability code. Numbers live in Shared/Heroes.luau.
	  Ability1  Jet Leap       burst of upward + forward movement
	  Ability2  Shock Grenade  arcing grenade: area damage + slow
	  Ultimate  Overdrive      faster fire, more damage, no reloading
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local CombatUtil = require(Shared.CombatUtil)

local Services = script.Parent.Parent:WaitForChild("Services")
local CombatService = require(Services.CombatService)
local StatusService = require(Services.StatusService)
local MotionService = require(Services.MotionService)
local ProjectileService = require(Services.ProjectileService)
local WeaponService = require(Services.WeaponService)
local FX = require(Services.FX)

local Kit = {}

-- Jet Leap
function Kit.Ability1(ctx)
	local ability = ctx.Ability
	local forward = CombatUtil.Flatten(ctx.AimDirection)
	local velocity = forward * ability.ForwardSpeed + Vector3.new(0, ability.UpSpeed, 0)
	MotionService.Dash(ctx.Character, velocity, ability.Duration)
	FX.All("Burst", { Position = ctx.Root.Position - Vector3.new(0, 2.5, 0), Color = ctx.Hero.AccentColor })
end

-- Shock Grenade: thrown slightly upward so it arcs, explodes on impact (or when its fuse runs out).
function Kit.Ability2(ctx)
	local ability = ctx.Ability
	local character = ctx.Character
	local direction = (ctx.AimDirection + Vector3.new(0, 0.18, 0)).Unit

	ProjectileService.Fire({
		Owner = character,
		Origin = ctx.AimOrigin,
		Velocity = direction * ability.Speed,
		Gravity = ability.Gravity,
		Radius = 0.7,
		Lifetime = 2.5,
		ExplodeOnExpire = true,
		Color = ctx.Hero.AccentColor,
		Style = "Grenade",
		Impact = "Explosion",
		ImpactRadius = ability.Radius,
		OnHit = function(_, position, normal)
			local center = if normal then position + normal * 0.5 else position
			CombatService.Explode(character, center, ability.Radius, ability.Damage, {
				Ability = ability.Name,
				OnHit = function(target)
					StatusService.Apply(target, "Slowed", { Speed = 1 - ability.SlowAmount }, ability.SlowDuration)
				end,
			})
		end,
	})
end

-- Overdrive
function Kit.Ultimate(ctx)
	local ability = ctx.Ability
	StatusService.Apply(ctx.Character, "Overdrive", {
		FireRate = ability.FireRateMult,
		DamageDealt = ability.DamageMult,
		InfiniteAmmo = 1,
	}, ability.Duration)
	WeaponService.Refill(ctx.Character)
	FX.All("Aura", { Character = ctx.Character, Duration = ability.Duration, Color = ctx.Hero.AccentColor })
end

return Kit
