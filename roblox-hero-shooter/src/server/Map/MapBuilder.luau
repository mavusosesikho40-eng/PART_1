--[[
	MapBuilder
	==========
	Builds the arena out of plain parts. The shipped place file already contains a pre-built copy
	in Workspace.Map that you can edit freely in Studio; this code only runs if that map is missing.
	You can also run it from Studio's command bar to rebuild the default arena:
		require(game.ServerScriptService.Server.Map.MapBuilder).Build().Parent = workspace

	ANY map works as long as Workspace.Map contains:
	  Spawns/<Team>      Folder of parts where heroes appear (one folder per team name)
	  SpawnRooms/<Team>  Invisible box part: heals that team inside, hurts enemies inside
	  SpawnDoors/<Team>  Folder of parts that block the exits during Setup
	  ControlPoint       Invisible part at the centre of the objective, with number
	                     attributes "Radius" and "Height" (the capture area)
	  PointVisual        (optional) Folder of parts recoloured to the owning team
	Parts tagged "Destructible" (CollectionService) with a "MaxHealth" attribute can be shot apart.

	The layout is built for the first team and copied for the second team rotated 180 degrees,
	so both sides are identical. Only plain Instance APIs are used, so tools/bake-map.luau can run
	this file outside Roblox to bake the map into the place file.
]]

local MapBuilder = {}

local COLORS = {
	Floor = Color3.fromRGB(46, 49, 58),
	FloorTile = Color3.fromRGB(55, 58, 68),
	Wall = Color3.fromRGB(84, 88, 100),
	Structure = Color3.fromRGB(70, 74, 86),
	Dark = Color3.fromRGB(36, 38, 46),
	Crate = Color3.fromRGB(150, 105, 60),
	Barricade = Color3.fromRGB(112, 118, 130),
	Neutral = Color3.fromRGB(235, 235, 240),
}

local DEFAULT_TEAMS = {
	{ Name = "Blue", Color = Color3.fromRGB(70, 160, 255), BrickColor = BrickColor.new("Bright blue") },
	{ Name = "Red", Color = Color3.fromRGB(255, 80, 80), BrickColor = BrickColor.new("Bright red") },
}

local ARENA_X = 160 -- half length (spawn to spawn runs along X)
local ARENA_Z = 100 -- half width
local WALL_HEIGHT = 34
local UPRIGHT = CFrame.Angles(0, 0, math.rad(90)) -- stands a cylinder on its end

local function newFolder(parent: Instance, name: string): Folder
	local folder = Instance.new("Folder")
	folder.Name = name
	folder.Parent = parent
	return folder
end

local function part(parent: Instance, props)
	local p = Instance.new(props.ClassName or "Part")
	p.Name = props.Name or "Part"
	p.Anchored = true
	p.TopSurface = Enum.SurfaceType.Smooth
	p.BottomSurface = Enum.SurfaceType.Smooth
	p.Size = props.Size
	p.CFrame = props.CFrame
	p.Color = props.Color or COLORS.Structure
	p.Material = props.Material or Enum.Material.SmoothPlastic
	if props.Shape then
		p.Shape = props.Shape
	end
	if props.Transparency then
		p.Transparency = props.Transparency
	end
	if props.CanCollide ~= nil then
		p.CanCollide = props.CanCollide
	end
	if props.CanQuery ~= nil then
		p.CanQuery = props.CanQuery
	end
	if props.CanTouch ~= nil then
		p.CanTouch = props.CanTouch
	end
	if props.CastShadow ~= nil then
		p.CastShadow = props.CastShadow
	end
	if props.Attributes then
		for key, value in props.Attributes do
			p:SetAttribute(key, value)
		end
	end
	if props.Tags then
		for _, tag in props.Tags do
			p:AddTag(tag)
		end
	end
	p.Parent = parent
	return p
end

-- Decorative, non-solid glowing part.
local function glow(parent: Instance, size: Vector3, cframe: CFrame, color: Color3, transparency: number?)
	return part(parent, {
		Name = "Glow",
		Size = size,
		CFrame = cframe,
		Color = color,
		Material = Enum.Material.Neon,
		Transparency = transparency,
		CanCollide = false,
		CanQuery = false,
		CanTouch = false,
		CastShadow = false,
	})
end

-- A block whose top surface runs from `bottom` to `top` (both are walking-surface points).
local function rampCFrame(bottom: Vector3, top: Vector3)
	local middle = (bottom + top) / 2
	local length = (top - bottom).Magnitude
	return CFrame.lookAt(middle, top) * CFrame.new(0, -0.5, 0), length
end

local function crate(parent: Instance, cframe: CFrame)
	return part(parent, {
		Name = "Crate",
		Size = Vector3.new(4, 4, 4),
		CFrame = cframe,
		Color = COLORS.Crate,
		Material = Enum.Material.WoodPlanks,
		Attributes = { MaxHealth = 150, RespawnTime = 25 },
		Tags = { "Destructible" },
	})
end

local function barricade(parent: Instance, cframe: CFrame, size: Vector3)
	return part(parent, {
		Name = "Barricade",
		Size = size,
		CFrame = cframe,
		Color = COLORS.Barricade,
		Material = Enum.Material.DiamondPlate,
		Attributes = { MaxHealth = 350, RespawnTime = 30 },
		Tags = { "Destructible" },
	})
end

--------------------------------------------------------------------------------
-- Everything that exists once: floor, outer walls and the control point.
--------------------------------------------------------------------------------
local function buildShared(map: Model, folders)
	local geometry, decor = folders.Geometry, folders.Decor

	part(geometry, {
		Name = "Floor",
		Size = Vector3.new(ARENA_X * 2 + 8, 2, ARENA_Z * 2 + 8),
		CFrame = CFrame.new(0, -1, 0),
		Color = COLORS.Floor,
		Material = Enum.Material.Concrete,
	})
	-- Checkerboard floor panels give a sense of distance and speed.
	for xIndex = 0, 7 do
		for zIndex = 0, 4 do
			if (xIndex + zIndex) % 2 == 0 then
				part(decor, {
					Name = "FloorPanel",
					Size = Vector3.new(38, 0.1, 38),
					CFrame = CFrame.new(-140 + xIndex * 40, 0.03, -80 + zIndex * 40),
					Color = COLORS.FloorTile,
					Material = Enum.Material.Slate,
					CanCollide = false,
					CanQuery = false,
					CanTouch = false,
				})
			end
		end
	end

	-- Outer walls
	for _, sign in { 1, -1 } do
		part(geometry, {
			Name = "OuterWall",
			Size = Vector3.new(ARENA_X * 2 + 8, WALL_HEIGHT, 4),
			CFrame = CFrame.new(0, WALL_HEIGHT / 2, sign * (ARENA_Z + 2)),
			Color = COLORS.Wall,
			Material = Enum.Material.Concrete,
		})
		part(geometry, {
			Name = "OuterWall",
			Size = Vector3.new(4, WALL_HEIGHT, ARENA_Z * 2 + 8),
			CFrame = CFrame.new(sign * (ARENA_X + 2), WALL_HEIGHT / 2, 0),
			Color = COLORS.Wall,
			Material = Enum.Material.Concrete,
		})
	end

	-- Control point: platform, glowing ring, core and a tall beacon you can see from anywhere.
	part(geometry, {
		Name = "PointPlatform",
		Shape = Enum.PartType.Cylinder,
		Size = Vector3.new(1.2, 40, 40),
		CFrame = CFrame.new(0, 0.6, 0) * UPRIGHT,
		Color = COLORS.Structure,
		Material = Enum.Material.Metal,
	})
	local pointVisual = folders.PointVisual
	part(pointVisual, {
		Name = "PointRing",
		Shape = Enum.PartType.Cylinder,
		Size = Vector3.new(0.4, 44, 44),
		CFrame = CFrame.new(0, 0.2, 0) * UPRIGHT,
		Color = COLORS.Neutral,
		Material = Enum.Material.Neon,
		CanCollide = false,
		CanQuery = false,
	})
	part(pointVisual, {
		Name = "PointCore",
		Shape = Enum.PartType.Cylinder,
		Size = Vector3.new(0.1, 8, 8),
		CFrame = CFrame.new(0, 1.25, 0) * UPRIGHT,
		Color = COLORS.Neutral,
		Material = Enum.Material.Neon,
		CanCollide = false,
		CanQuery = false,
	})
	part(pointVisual, {
		Name = "Beacon",
		Shape = Enum.PartType.Cylinder,
		Size = Vector3.new(70, 1.5, 1.5),
		CFrame = CFrame.new(0, 36.2, 0) * UPRIGHT,
		Color = COLORS.Neutral,
		Material = Enum.Material.Neon,
		Transparency = 0.55,
		CanCollide = false,
		CanQuery = false,
		CastShadow = false,
	})
	part(map, {
		Name = "ControlPoint",
		Size = Vector3.new(2, 2, 2),
		CFrame = CFrame.new(0, 2.2, 0),
		Transparency = 1,
		CanCollide = false,
		CanQuery = false,
		CanTouch = false,
		Attributes = { Radius = 20, Height = 10 },
	})
end

--------------------------------------------------------------------------------
-- One team's half. Coordinates are written for the first team (negative X);
-- `place` rotates them for the second team.
--------------------------------------------------------------------------------
local function buildSide(team, transform: CFrame, folders)
	local geometry, cover, decor = folders.Geometry, folders.Cover, folders.Decor
	local color = team.Color
	local function place(cframe: CFrame): CFrame
		return transform * cframe
	end

	-- Spawn room: walls, roof, a door gap with a team-coloured door, spawn pads, heal zone.
	local roomProps = { Color = COLORS.Structure, Material = Enum.Material.Concrete }
	local function roomPart(name: string, size: Vector3, cframe: CFrame)
		return part(geometry, { Name = name, Size = size, CFrame = place(cframe), Color = roomProps.Color, Material = roomProps.Material })
	end
	roomPart("SpawnWall", Vector3.new(2, 18, 18), CFrame.new(-123, 9, -17))
	roomPart("SpawnWall", Vector3.new(2, 18, 18), CFrame.new(-123, 9, 17))
	roomPart("SpawnLintel", Vector3.new(2, 6, 16), CFrame.new(-123, 15, 0))
	roomPart("SpawnWall", Vector3.new(37, 18, 2), CFrame.new(-141.5, 9, -25))
	roomPart("SpawnWall", Vector3.new(37, 18, 2), CFrame.new(-141.5, 9, 25))
	roomPart("SpawnRoof", Vector3.new(38, 1.5, 52), CFrame.new(-141, 18.75, 0))
	glow(decor, Vector3.new(0.4, 0.6, 16), place(CFrame.new(-121.8, 12.3, 0)), color)
	glow(decor, Vector3.new(0.4, 2, 12), place(CFrame.new(-121.8, 15.5, 0)), color)

	local doors = newFolder(folders.SpawnDoors, team.Name)
	part(doors, {
		Name = "SpawnDoor",
		Size = Vector3.new(1, 12, 16),
		CFrame = place(CFrame.new(-123, 6, 0)),
		Color = color,
		Material = Enum.Material.Neon,
		Transparency = 0.45,
		CastShadow = false,
	})

	local pads = newFolder(folders.Spawns, team.Name)
	for _, x in { -150, -138 } do
		for _, z in { -12, 0, 12 } do
			part(pads, {
				Name = "SpawnPad",
				Size = Vector3.new(5, 0.3, 5),
				CFrame = place(CFrame.new(x, 0.15, z)),
				Color = color,
				Material = Enum.Material.Neon,
				Transparency = 0.25,
				CanCollide = false,
				CanQuery = false,
				CanTouch = false,
			})
		end
	end

	local spawnLocation = part(folders.SpawnLocations, {
		ClassName = "SpawnLocation",
		Name = team.Name .. "SpawnLocation",
		Size = Vector3.new(6, 1, 6),
		CFrame = place(CFrame.new(-145, 0.5, 0)),
		Transparency = 1,
		CanCollide = false,
		CanQuery = false,
		CanTouch = false,
	})
	spawnLocation.Neutral = false
	spawnLocation.TeamColor = team.BrickColor
	spawnLocation.Duration = 0 -- no spawn force field
	spawnLocation.AllowTeamChangeOnTouch = false

	part(folders.SpawnRooms, {
		Name = team.Name,
		Size = Vector3.new(36, 18, 50),
		CFrame = place(CFrame.new(-142, 9, 0)),
		Transparency = 1,
		CanCollide = false,
		CanQuery = false,
		CanTouch = false,
	})

	-- Main lane: barricades and crates between spawn and the gate.
	barricade(cover, place(CFrame.new(-100, 2.5, -10)), Vector3.new(1.5, 5, 10))
	barricade(cover, place(CFrame.new(-100, 2.5, 15)), Vector3.new(1.5, 5, 8))
	barricade(cover, place(CFrame.new(-64, 2.5, 0)), Vector3.new(1.5, 5, 10))
	crate(cover, place(CFrame.new(-82, 2, -3) * CFrame.Angles(0, math.rad(15), 0)))
	crate(cover, place(CFrame.new(-86, 2, 5)))
	crate(cover, place(CFrame.new(-46, 2, -26) * CFrame.Angles(0, math.rad(-20), 0)))
	crate(cover, place(CFrame.new(-40, 2, 30)))
	glow(decor, Vector3.new(56, 0.1, 0.5), place(CFrame.new(-92, 0.06, -9)), color)
	glow(decor, Vector3.new(56, 0.1, 0.5), place(CFrame.new(-92, 0.06, 9)), color)

	-- Gate: two pillars and an overhead beam framing the lane.
	for _, z in { -16, 16 } do
		part(geometry, { Name = "GatePillar", Size = Vector3.new(6, 16, 6), CFrame = place(CFrame.new(-60, 8, z)), Color = COLORS.Wall, Material = Enum.Material.Concrete })
	end
	part(geometry, { Name = "GateBeam", Size = Vector3.new(6, 3, 38), CFrame = place(CFrame.new(-60, 17.5, 0)), Color = COLORS.Wall, Material = Enum.Material.Concrete })
	glow(decor, Vector3.new(6.2, 0.4, 26), place(CFrame.new(-60, 15.85, 0)), color)

	-- Tower: a second floor with low walls to shoot over, reached by a ramp.
	part(geometry, { Name = "TowerFloor", Size = Vector3.new(28, 1, 28), CFrame = place(CFrame.new(-78, 10.5, 60)), Material = Enum.Material.Metal })
	for _, corner in { { -90.5, 47.5 }, { -65.5, 47.5 }, { -90.5, 72.5 }, { -65.5, 72.5 } } do
		part(geometry, { Name = "TowerPillar", Size = Vector3.new(3, 10, 3), CFrame = place(CFrame.new(corner[1], 5, corner[2])), Color = COLORS.Wall, Material = Enum.Material.Concrete })
	end
	local parapets = {
		{ Vector3.new(28, 3.5, 1), CFrame.new(-78, 12.75, 73.5) },
		{ Vector3.new(1, 3.5, 28), CFrame.new(-91.5, 12.75, 60) },
		{ Vector3.new(1, 3.5, 28), CFrame.new(-64.5, 12.75, 60) },
		{ Vector3.new(12.5, 3.5, 1), CFrame.new(-85.75, 12.75, 46.5) },
		{ Vector3.new(8.5, 3.5, 1), CFrame.new(-68.25, 12.75, 46.5) },
	}
	for _, parapet in parapets do
		part(geometry, { Name = "Parapet", Size = parapet[1], CFrame = place(parapet[2]), Color = COLORS.Wall, Material = Enum.Material.Concrete })
	end
	glow(decor, Vector3.new(28.2, 0.3, 0.3), place(CFrame.new(-78, 10, 45.9)), color)
	local towerRamp, towerRampLength = rampCFrame(Vector3.new(-76, 0, 22), Vector3.new(-76, 11, 46))
	part(geometry, { Name = "Ramp", Size = Vector3.new(7, 1, towerRampLength + 1), CFrame = place(towerRamp), Material = Enum.Material.DiamondPlate })
	crate(cover, place(CFrame.new(-84, 2, 58)))
	crate(cover, place(CFrame.new(-72, 2, 66) * CFrame.Angles(0, math.rad(30), 0)))

	-- Courtyard: a raised platform with a ramp and plenty of breakable crates.
	part(geometry, { Name = "Platform", Size = Vector3.new(20, 6, 16), CFrame = place(CFrame.new(-80, 3, -62)), Material = Enum.Material.Concrete })
	local yardRamp, yardRampLength = rampCFrame(Vector3.new(-80, 0, -36), Vector3.new(-80, 6, -54))
	part(geometry, { Name = "Ramp", Size = Vector3.new(8, 1, yardRampLength + 1), CFrame = place(yardRamp), Material = Enum.Material.DiamondPlate })
	part(geometry, { Name = "PlatformWall", Size = Vector3.new(1, 3, 16), CFrame = place(CFrame.new(-70.5, 7.5, -62)), Color = COLORS.Wall, Material = Enum.Material.Concrete })
	glow(decor, Vector3.new(20.2, 0.3, 0.3), place(CFrame.new(-80, 6.05, -53.9)), color)
	crate(cover, place(CFrame.new(-100, 2, -45)))
	crate(cover, place(CFrame.new(-60, 2, -80) * CFrame.Angles(0, math.rad(10), 0)))
	crate(cover, place(CFrame.new(-55, 2, -50)))
	crate(cover, place(CFrame.new(-105, 2, -78)))

	-- Around the point
	part(geometry, { Name = "MidWall", Size = Vector3.new(24, 9, 3), CFrame = place(CFrame.new(-22, 4.5, 40)), Color = COLORS.Wall, Material = Enum.Material.Concrete })
	glow(decor, Vector3.new(24.2, 0.4, 3.2), place(CFrame.new(-22, 9.1, 40)), color)
	part(geometry, { Name = "MidPillar", Size = Vector3.new(4, 12, 4), CFrame = place(CFrame.new(-16, 6, -24)), Color = COLORS.Wall, Material = Enum.Material.Concrete })
	part(geometry, { Name = "LowCover", Size = Vector3.new(2, 4, 10), CFrame = place(CFrame.new(-30, 2, -6)), Material = Enum.Material.Metal })
	part(geometry, { Name = "PointCover", Size = Vector3.new(1.5, 4, 6), CFrame = place(CFrame.new(-10, 3.2, 0)), Material = Enum.Material.Metal })
	part(geometry, { Name = "PointCover", Size = Vector3.new(6, 4, 1.5), CFrame = place(CFrame.new(0, 3.2, 8)), Material = Enum.Material.Metal })
	crate(cover, place(CFrame.new(-26, 2, 18)))
	crate(cover, place(CFrame.new(-8, 2, -30)))

	-- Lamps
	for _, spot in { { -110, -34 }, { -110, 34 }, { -40, -64 }, { -40, 64 } } do
		part(geometry, { Name = "LampPost", Size = Vector3.new(1, 14, 1), CFrame = place(CFrame.new(spot[1], 7, spot[2])), Color = COLORS.Dark, Material = Enum.Material.Metal })
		local lamp = glow(decor, Vector3.new(2.4, 0.8, 2.4), place(CFrame.new(spot[1], 14.4, spot[2])), color)
		local light = Instance.new("PointLight")
		light.Color = color
		light.Range = 28
		light.Brightness = 1.4
		light.Parent = lamp
	end

	-- Team-coloured trim along the bottom of the outer walls on this half.
	glow(decor, Vector3.new(ARENA_X, 0.6, 0.4), place(CFrame.new(-ARENA_X / 2, 0.6, ARENA_Z - 0.3)), color)
	glow(decor, Vector3.new(ARENA_X, 0.6, 0.4), place(CFrame.new(-ARENA_X / 2, 0.6, -ARENA_Z + 0.3)), color)
	glow(decor, Vector3.new(0.4, 0.6, ARENA_Z * 2), place(CFrame.new(-ARENA_X + 0.3, 0.6, 0)), color)
end

-- Builds and returns the map Model (not parented). `teams` defaults to Blue vs Red.
function MapBuilder.Build(teams)
	teams = teams or DEFAULT_TEAMS
	local map = Instance.new("Model")
	map.Name = "Map"

	local folders = {}
	for _, name in { "Geometry", "Cover", "Decor", "PointVisual", "Spawns", "SpawnRooms", "SpawnDoors", "SpawnLocations" } do
		folders[name] = newFolder(map, name)
	end

	buildShared(map, folders)
	for index, team in teams do
		local transform = if index == 1 then CFrame.new() else CFrame.Angles(0, math.pi, 0)
		buildSide(team, transform, folders)
	end
	return map
end

-- Default lighting for the arena (only used when the map is built at runtime; the place file
-- already has these settings).
function MapBuilder.SetupLighting(lighting: Lighting)
	lighting.ClockTime = 15.2
	lighting.Brightness = 2.6
	lighting.Ambient = Color3.fromRGB(60, 60, 72)
	lighting.OutdoorAmbient = Color3.fromRGB(125, 125, 140)
	lighting.EnvironmentDiffuseScale = 0.6
	lighting.EnvironmentSpecularScale = 0.7
	lighting.GlobalShadows = true

	if not lighting:FindFirstChildOfClass("Atmosphere") then
		local atmosphere = Instance.new("Atmosphere")
		atmosphere.Density = 0.3
		atmosphere.Offset = 0.25
		atmosphere.Color = Color3.fromRGB(199, 185, 170)
		atmosphere.Decay = Color3.fromRGB(110, 90, 140)
		atmosphere.Glare = 0.2
		atmosphere.Haze = 1.4
		atmosphere.Parent = lighting
	end
	if not lighting:FindFirstChildOfClass("BloomEffect") then
		local bloom = Instance.new("BloomEffect")
		bloom.Intensity = 0.7
		bloom.Size = 24
		bloom.Threshold = 1.5
		bloom.Parent = lighting
	end
	if not lighting:FindFirstChildOfClass("ColorCorrectionEffect") then
		local grading = Instance.new("ColorCorrectionEffect")
		grading.Contrast = 0.08
		grading.Saturation = 0.12
		grading.Parent = lighting
	end
end

return MapBuilder
