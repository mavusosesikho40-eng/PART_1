--[[
	MapService
	==========
	Finds (or builds) Workspace.Map and answers questions about it: where to spawn, where the
	control point is, whether someone is standing in a spawn room, and opening the spawn doors.

	See Map/MapBuilder.luau for the pieces a custom map needs.
]]

local Lighting = game:GetService("Lighting")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local MapBuilder = require(script.Parent.Parent.Map.MapBuilder)

local MapService = {}

local map: Model? = nil
local effects: Folder? = nil
local rng = Random.new()

function MapService.Init()
	map = workspace:FindFirstChild("Map")
	if not map then
		-- No map in the place (e.g. scripts were copied in by hand), so build the default arena.
		map = MapBuilder.Build(Config.Teams)
		map.Parent = workspace
		MapBuilder.SetupLighting(Lighting)
	end

	for _, required in { "Spawns", "SpawnRooms", "SpawnDoors", "ControlPoint" } do
		if not map:FindFirstChild(required) then
			warn("[MapService] Workspace.Map is missing '" .. required .. "'. See MapBuilder.luau for what a map needs.")
		end
	end

	effects = workspace:FindFirstChild("Effects")
	if not effects then
		effects = Instance.new("Folder")
		effects.Name = "Effects"
		effects.Parent = workspace
	end
end

function MapService.GetMap()
	return map
end

-- Server-made visuals (barriers, zones) live here and are wiped between rounds.
function MapService.GetEffectsFolder(): Folder
	return effects :: Folder
end

function MapService.ClearEffects()
	if effects then
		effects:ClearAllChildren()
	end
end

function MapService.GetControlPoint(): BasePart?
	local point = map and map:FindFirstChild("ControlPoint")
	return if point and point:IsA("BasePart") then point else nil
end

function MapService.GetPointPosition(): Vector3
	local point = MapService.GetControlPoint()
	return if point then point.Position else Vector3.new(0, 2, 0)
end

-- A random spawn spot for `team`, facing the objective.
function MapService.GetSpawnCFrame(team: string): CFrame
	local pads = {}
	local spawns = map and map:FindFirstChild("Spawns")
	local folder = spawns and spawns:FindFirstChild(team)
	if folder then
		for _, child in folder:GetChildren() do
			if child:IsA("BasePart") then
				table.insert(pads, child)
			end
		end
	end
	if #pads == 0 then
		warn("[MapService] No spawn pads for team " .. tostring(team))
		return CFrame.new(0, 10, 0)
	end

	local pad = pads[rng:NextInteger(1, #pads)]
	local position = pad.Position
		+ Vector3.new(rng:NextNumber(-1.5, 1.5), pad.Size.Y / 2 + 3.2, rng:NextNumber(-1.5, 1.5))
	local target = MapService.GetPointPosition()
	local lookAt = Vector3.new(target.X, position.Y, target.Z)
	if (lookAt - position).Magnitude < 1 then
		lookAt = position + Vector3.new(0, 0, -1)
	end
	return CFrame.lookAt(position, lookAt)
end

local function insideBox(part: BasePart, position: Vector3): boolean
	local relative = part.CFrame:PointToObjectSpace(position)
	local half = part.Size / 2
	return math.abs(relative.X) <= half.X and math.abs(relative.Y) <= half.Y and math.abs(relative.Z) <= half.Z
end

function MapService.IsInSpawnRoom(position: Vector3, team: string?): boolean
	local rooms = map and map:FindFirstChild("SpawnRooms")
	local room = rooms and team and rooms:FindFirstChild(team)
	return room ~= nil and room:IsA("BasePart") and insideBox(room, position)
end

-- Closed doors are solid and block shots; open doors vanish.
function MapService.SetDoorsOpen(open: boolean)
	local doors = map and map:FindFirstChild("SpawnDoors")
	if not doors then
		return
	end
	for _, door in doors:GetDescendants() do
		if door:IsA("BasePart") then
			door.CanCollide = not open
			door.CanQuery = not open
			door.Transparency = if open then 1 else 0.45
		end
	end
end

return MapService
