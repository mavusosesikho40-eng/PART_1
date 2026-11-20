--[[
	KitUtil
	=======
	Helpers shared by the hero kits: finding the ground, clamping ranges, and drawing area zones.
	(This module has no matching hero, so AbilityService skips it when loading kits.)
]]

local Debris = game:GetService("Debris")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local CombatUtil = require(Shared.CombatUtil)

local Services = script.Parent.Parent:WaitForChild("Services")
local MapService = require(Services.MapService)

local KitUtil = {}

-- Raycast params that only see the world (no characters).
function KitUtil.WorldParams(): RaycastParams
	local params = RaycastParams.new()
	params.FilterType = Enum.RaycastFilterType.Exclude
	params.FilterDescendantsInstances = CombatUtil.GetAllCharacters()
	params.IgnoreWater = true
	return params
end

-- The floor directly below (or slightly above) a point.
function KitUtil.GroundAt(position: Vector3): Vector3
	local result = workspace:Raycast(position + Vector3.new(0, 4, 0), Vector3.new(0, -80, 0), KitUtil.WorldParams())
	return if result then result.Position else position
end

function KitUtil.ClampRange(from: Vector3, to: Vector3, maxRange: number): Vector3
	local offset = to - from
	if offset.Magnitude > maxRange then
		return from + offset.Unit * maxRange
	end
	return to
end

-- A glowing disc on the ground that marks an area effect. Removed after `duration`.
function KitUtil.MakeZone(position: Vector3, radius: number, color: Color3, duration: number, transparency: number?)
	local disc = Instance.new("Part")
	disc.Name = "Zone"
	disc.Shape = Enum.PartType.Cylinder
	disc.Size = Vector3.new(0.4, radius * 2, radius * 2)
	disc.CFrame = CFrame.new(position + Vector3.new(0, 0.25, 0)) * CFrame.Angles(0, 0, math.rad(90))
	disc.Anchored = true
	disc.CanCollide = false
	disc.CanQuery = false
	disc.CanTouch = false
	disc.CastShadow = false
	disc.Material = Enum.Material.Neon
	disc.Color = color
	disc.Transparency = transparency or 0.65
	disc.Parent = MapService.GetEffectsFolder()
	Debris:AddItem(disc, duration)
	return disc
end

-- Runs `onTick` every `interval` seconds for `duration` seconds (stops early if `keepGoing` says so).
function KitUtil.Repeat(duration: number, interval: number, onTick, keepGoing)
	task.spawn(function()
		local elapsed = 0
		while elapsed < duration do
			if keepGoing and not keepGoing() then
				return
			end
			onTick(elapsed)
			task.wait(interval)
			elapsed += interval
		end
	end)
end

return KitUtil
