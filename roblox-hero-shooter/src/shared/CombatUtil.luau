--[[
	CombatUtil
	==========
	Small helpers used by both the server (hit validation) and the client (aiming), so both
	sides agree on things like where a shot starts and what it can pass through.
]]

local CollectionService = game:GetService("CollectionService")

local CombatUtil = {}

-- Walks up from a part to the character Model that owns it (anything with a Humanoid).
function CombatUtil.GetCharacterFromPart(part)
	local current = part
	while current and current ~= workspace and current ~= game do
		if current:IsA("Model") and current:FindFirstChildOfClass("Humanoid") then
			return current
		end
		current = current.Parent
	end
	return nil
end

function CombatUtil.GetHumanoid(character)
	return if character then character:FindFirstChildOfClass("Humanoid") else nil
end

function CombatUtil.GetRoot(character)
	if not character then
		return nil
	end
	local root = character:FindFirstChild("HumanoidRootPart")
	return if root and root:IsA("BasePart") then root else nil
end

function CombatUtil.IsAlive(character): boolean
	if not character or not character.Parent then
		return false
	end
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	return humanoid ~= nil and humanoid.Health > 0
end

function CombatUtil.GetTeam(character)
	return if character then character:GetAttribute("Team") else nil
end

function CombatUtil.AreEnemies(a, b): boolean
	local teamA, teamB = CombatUtil.GetTeam(a), CombatUtil.GetTeam(b)
	return teamA ~= nil and teamB ~= nil and teamA ~= "" and teamB ~= "" and teamA ~= teamB
end

-- Every fighter (players and bots) currently in the world.
function CombatUtil.GetAllCharacters()
	local list = {}
	for _, child in workspace:GetChildren() do
		if child:IsA("Model") and child:GetAttribute("CombatantId") then
			table.insert(list, child)
		end
	end
	return list
end

-- Where shots come out: just right of the head, roughly where the weapon is held.
function CombatUtil.GetMuzzle(character)
	local root = CombatUtil.GetRoot(character)
	if not root then
		return nil
	end
	local head = character:FindFirstChild("Head")
	local base = if head and head:IsA("BasePart") then head.Position else root.Position + Vector3.new(0, 1.5, 0)
	return base + root.CFrame.RightVector * 1 - Vector3.new(0, 0.4, 0)
end

-- Barriers that belong to a team. Allies shoot straight through their own team's barriers.
function CombatUtil.GetTeamBarriers(team)
	local list = {}
	if team == nil or team == "" then
		return list
	end
	for _, barrier in CollectionService:GetTagged("Barrier") do
		if barrier:GetAttribute("Team") == team then
			table.insert(list, barrier)
		end
	end
	return list
end

-- Raycast params for a shot fired by `shooter`: ignores the shooter and their team's barriers.
-- With ignoreCharacters = true it only sees the world (used for line-of-sight checks).
function CombatUtil.ShotParams(shooter, ignoreCharacters: boolean?): RaycastParams
	local ignore = { shooter }
	for _, barrier in CombatUtil.GetTeamBarriers(shooter:GetAttribute("Team")) do
		table.insert(ignore, barrier)
	end
	if ignoreCharacters then
		for _, character in CombatUtil.GetAllCharacters() do
			table.insert(ignore, character)
		end
	end
	local params = RaycastParams.new()
	params.FilterType = Enum.RaycastFilterType.Exclude
	params.FilterDescendantsInstances = ignore
	params.IgnoreWater = true
	return params
end

-- Rotates `direction` randomly inside a cone `spreadDegrees` wide.
function CombatUtil.SpreadDirection(direction: Vector3, spreadDegrees: number, rng: Random): Vector3
	local unit = direction.Unit
	if spreadDegrees <= 0 then
		return unit
	end
	local up = if math.abs(unit.Y) > 0.99 then Vector3.xAxis else Vector3.yAxis
	local frame = CFrame.lookAt(Vector3.zero, unit, up)
	local angle = math.rad(spreadDegrees) * math.sqrt(rng:NextNumber())
	local roll = rng:NextNumber() * math.pi * 2
	return (frame * CFrame.Angles(0, 0, roll) * CFrame.Angles(angle, 0, 0)).LookVector
end

-- Damage multiplier for a weapon at a given distance (1 = full damage).
function CombatUtil.Falloff(weapon, distance: number): number
	local start, finish, minimum = weapon.FalloffStart, weapon.FalloffEnd, weapon.FalloffMin
	if not start or not finish or not minimum then
		return 1
	end
	if distance <= start then
		return 1
	elseif distance >= finish then
		return minimum
	end
	local alpha = (distance - start) / (finish - start)
	return 1 + (minimum - 1) * alpha
end

-- Horizontal unit vector (falls back to "forward" if the input points straight up or down).
function CombatUtil.Flatten(vector: Vector3): Vector3
	local flat = Vector3.new(vector.X, 0, vector.Z)
	return if flat.Magnitude > 1e-3 then flat.Unit else Vector3.new(0, 0, -1)
end

-- Rejects NaN, infinity and non-Vector3 values (never trust what a client sends!).
function CombatUtil.IsFiniteVector(value): boolean
	if typeof(value) ~= "Vector3" then
		return false
	end
	for _, component in { value.X, value.Y, value.Z } do
		if component ~= component or math.abs(component) > 1e6 then
			return false
		end
	end
	return true
end

-- The Y rotation that makes a CFrame face `look` (used to point cameras at spawn).
function CombatUtil.YawFromLook(look: Vector3): number
	return math.atan2(-look.X, -look.Z)
end

return CombatUtil
