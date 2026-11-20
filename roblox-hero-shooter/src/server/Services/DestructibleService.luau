--[[
	DestructibleService
	===================
	Any part tagged "Destructible" can be shot to pieces. Give it a MaxHealth attribute (default 200)
	and optionally RespawnTime (seconds, default 30). Broken cover rebuilds itself later, and all
	cover is restored at the start of every round.

	Barriers (like Bulwark's Aegis Wall) are destructibles with a "Team" attribute (their own team
	cannot damage them) and "Permanent" = true (they are deleted instead of rebuilt).
]]

local CollectionService = game:GetService("CollectionService")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local CombatUtil = require(Shared.CombatUtil)
local GameState = require(script.Parent.GameState)
local FX = require(script.Parent.FX)

local TAG = "Destructible"
local DEFAULT_HEALTH = 200
local DEFAULT_RESPAWN = 30

local DestructibleService = {}

local originals = {} -- [part] = saved look, so it can be rebuilt
local broken = {} -- [part] = os.clock() time to rebuild

local function setup(instance)
	if not instance:IsA("BasePart") or originals[instance] then
		return
	end
	local maxHealth = instance:GetAttribute("MaxHealth") or DEFAULT_HEALTH
	instance:SetAttribute("MaxHealth", maxHealth)
	instance:SetAttribute("Health", maxHealth)
	originals[instance] = {
		Color = instance.Color,
		Transparency = instance.Transparency,
		CanCollide = instance.CanCollide,
		CanQuery = instance.CanQuery,
	}
	instance.AncestryChanged:Connect(function()
		if not instance:IsDescendantOf(game) then
			originals[instance] = nil
			broken[instance] = nil
		end
	end)
end

local function restore(part: BasePart)
	local original = originals[part]
	if not original then
		return
	end
	part.Color = original.Color
	part.Transparency = original.Transparency
	part.CanCollide = original.CanCollide
	part.CanQuery = original.CanQuery
	part:SetAttribute("Health", part:GetAttribute("MaxHealth"))
	broken[part] = nil
end

local function shatter(part: BasePart)
	local original = originals[part]
	FX.All("Shatter", {
		CFrame = part.CFrame,
		Size = part.Size,
		Color = if original then original.Color else part.Color,
		Material = part.Material,
	})
	if part:GetAttribute("Permanent") then
		part:Destroy()
		return
	end
	part.Transparency = 1
	part.CanCollide = false
	part.CanQuery = false
	broken[part] = os.clock() + (part:GetAttribute("RespawnTime") or DEFAULT_RESPAWN)
end

function DestructibleService.IsDestructible(part): boolean
	return part ~= nil and originals[part] ~= nil and (part:GetAttribute("Health") or 0) > 0
end

function DestructibleService.Damage(part: BasePart, amount: number, attacker: Model?): number
	if not DestructibleService.IsDestructible(part) or not GameState.DamageEnabled or amount <= 0 then
		return 0
	end
	local team = part:GetAttribute("Team")
	if team and attacker and attacker:GetAttribute("Team") == team then
		return 0 -- you can't break your own team's barrier
	end

	local health = part:GetAttribute("Health")
	local dealt = math.min(health, amount)
	health -= dealt
	part:SetAttribute("Health", health)

	if health <= 0 then
		shatter(part)
	elseif not team then
		-- Cover gets darker as it is damaged so players can see it is about to break.
		local original = originals[part]
		local fraction = health / part:GetAttribute("MaxHealth")
		part.Color = original.Color:Lerp(Color3.new(0.08, 0.08, 0.08), (1 - fraction) * 0.55)
	end
	return dealt
end

-- Explosions damage every destructible within `radius` (less at the edge).
function DestructibleService.DamageInRadius(position: Vector3, radius: number, damage: number, attacker: Model?)
	for part in originals do
		if part.Parent and (part:GetAttribute("Health") or 0) > 0 then
			local relative = part.CFrame:PointToObjectSpace(position)
			local half = part.Size / 2
			local closest = Vector3.new(
				math.clamp(relative.X, -half.X, half.X),
				math.clamp(relative.Y, -half.Y, half.Y),
				math.clamp(relative.Z, -half.Z, half.Z)
			)
			local distance = (relative - closest).Magnitude
			if distance <= radius then
				DestructibleService.Damage(part, damage * (1 - 0.5 * distance / radius), attacker)
			end
		end
	end
end

-- Rebuild everything (start of a round).
function DestructibleService.ResetAll()
	for part in originals do
		if part.Parent then
			restore(part)
		end
	end
end

function DestructibleService.Init()
	for _, instance in CollectionService:GetTagged(TAG) do
		setup(instance)
	end
	CollectionService:GetInstanceAddedSignal(TAG):Connect(setup)

	-- Rebuild broken cover once its timer is up, but never on top of someone.
	task.spawn(function()
		while true do
			task.wait(1)
			local now = os.clock()
			for part, rebuildAt in broken do
				if now >= rebuildAt then
					local params = OverlapParams.new()
					params.FilterType = Enum.RaycastFilterType.Include
					params.FilterDescendantsInstances = CombatUtil.GetAllCharacters()
					if #workspace:GetPartBoundsInBox(part.CFrame, part.Size, params) == 0 then
						restore(part)
					else
						broken[part] = now + 2
					end
				end
			end
		end
	end)
end

return DestructibleService
