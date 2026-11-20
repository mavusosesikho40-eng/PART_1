--[[
	ProjectileService
	=================
	Moves projectiles on the server (so hits are fair) using a sphere cast every frame. Clients only
	draw them: each client receives the start position/velocity and simulates the same flight.

		ProjectileService.Fire({
			Owner = character,             -- who fired it (never hits its owner)
			Origin = Vector3, Velocity = Vector3,
			Gravity = 0,                   -- studs/s^2 pulling it down (grenades arc)
			Radius = 0.5,                  -- hit size
			Lifetime = 3,                  -- seconds before it fizzles
			HitsAllies = false,            -- true for healing shots
			ExplodeOnExpire = false,       -- call OnHit where it fizzles (grenades)
			Color = Color3, Style = "Volta",
			Impact = "Explosion" | "Spark", ImpactRadius = 7,   -- what clients draw on impact
			OnHit = function(hitPart: BasePart?, position: Vector3, normal: Vector3?) end,
			Id = "123_4", PredictedBy = player,  -- the shooter already drew it locally
		})
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local CombatUtil = require(Shared.CombatUtil)
local FX = require(script.Parent.FX)

local ProjectileService = {}

local active = {}
local counter = 0

function ProjectileService.Fire(def)
	counter += 1
	local projectile = {
		Def = def,
		Id = def.Id or ("S" .. counter),
		Position = def.Origin,
		Velocity = def.Velocity,
		Gravity = def.Gravity or 0,
		Radius = def.Radius or 0.5,
		Lifetime = def.Lifetime or 3,
		Age = 0,
		Team = def.Owner:GetAttribute("Team"),
	}
	table.insert(active, projectile)

	FX.Except(def.PredictedBy, "Projectile", {
		Id = projectile.Id,
		Origin = projectile.Position,
		Velocity = projectile.Velocity,
		Gravity = projectile.Gravity,
		Lifetime = projectile.Lifetime,
		Radius = projectile.Radius,
		Style = def.Style,
		Color = def.Color,
	})
	return projectile.Id
end

local function buildParams(projectile): RaycastParams
	local ignore = { projectile.Def.Owner }
	for _, barrier in CombatUtil.GetTeamBarriers(projectile.Team) do
		table.insert(ignore, barrier)
	end
	if not projectile.Def.HitsAllies then
		for _, character in CombatUtil.GetAllCharacters() do
			if character:GetAttribute("Team") == projectile.Team then
				table.insert(ignore, character)
			end
		end
	end
	local params = RaycastParams.new()
	params.FilterType = Enum.RaycastFilterType.Exclude
	params.FilterDescendantsInstances = ignore
	params.IgnoreWater = true
	return params
end

local function finish(index: number, projectile, hitPart: BasePart?, position: Vector3, normal: Vector3?)
	table.remove(active, index)
	local def = projectile.Def
	FX.All("ProjectileEnd", {
		Id = projectile.Id,
		Position = position,
		Impact = def.Impact,
		Radius = def.ImpactRadius,
		Color = def.Color,
	})
	if def.OnHit and (hitPart or def.ExplodeOnExpire) then
		local ok, err = pcall(def.OnHit, hitPart, position, normal)
		if not ok then
			warn("[ProjectileService] OnHit error: " .. tostring(err))
		end
	end
end

RunService.Heartbeat:Connect(function(dt)
	for index = #active, 1, -1 do
		local projectile = active[index]
		projectile.Age += dt
		local gravity = Vector3.new(0, -projectile.Gravity, 0)
		local displacement = projectile.Velocity * dt + gravity * (0.5 * dt * dt)
		projectile.Velocity += gravity * dt

		local result = nil
		if displacement.Magnitude > 1e-3 then
			result = workspace:Spherecast(projectile.Position, projectile.Radius, displacement, buildParams(projectile))
		end

		if result then
			finish(index, projectile, result.Instance, result.Position, result.Normal)
		else
			projectile.Position += displacement
			if projectile.Age >= projectile.Lifetime then
				finish(index, projectile, nil, projectile.Position, nil)
			end
		end
	end
end)

return ProjectileService
