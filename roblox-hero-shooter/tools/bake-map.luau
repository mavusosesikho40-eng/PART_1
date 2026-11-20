--[[
	Bakes the default arena (src/server/Map/MapBuilder.luau) into map/Map.rbxm so it ships inside
	the place file and you can see and edit it in Studio.

	Run from the roblox-hero-shooter folder:   lune run tools/bake-map
	(Lune is a standalone Luau runtime: https://lune-org.github.io/docs)
]]

local fs = require("@lune/fs")
local luau = require("@lune/luau")
local roblox = require("@lune/roblox")

-- The Roblox types MapBuilder and Config use, provided by Lune.
local environment = {
	Instance = roblox.Instance,
	Vector3 = roblox.Vector3,
	Vector2 = roblox.Vector2,
	CFrame = roblox.CFrame,
	Color3 = roblox.Color3,
	BrickColor = roblox.BrickColor,
	Enum = roblox.Enum,
}

local function loadModule(path: string)
	local chunk = luau.load(fs.readFile(path), { debugName = path, environment = environment })
	return chunk()
end

local Config = loadModule("src/shared/Config.luau")
local MapBuilder = loadModule("src/server/Map/MapBuilder.luau")

local map = MapBuilder.Build(Config.Teams)

local parts = 0
for _, descendant in map:GetDescendants() do
	if descendant:IsA("BasePart") then
		parts += 1
	end
end

if not fs.isDir("map") then
	fs.writeDir("map")
end
fs.writeFile("map/Map.rbxm", roblox.serializeModel({ map }))
print(string.format("Baked map/Map.rbxm (%d parts)", parts))
