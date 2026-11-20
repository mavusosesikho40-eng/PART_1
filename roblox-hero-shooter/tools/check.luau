--[[
	Project checks you can run without opening Studio:
	  1. every .luau file compiles with the real Luau compiler
	  2. hero, synergy and config data is consistent (catches typos after you add a hero)
	  3. every hero has a kit with Ability1, Ability2 and Ultimate
	  4. the map builder produces every piece the game needs

	Run from the roblox-hero-shooter folder:   lune run tools/check
]]

local fs = require("@lune/fs")
local luau = require("@lune/luau")
local process = require("@lune/process")
local roblox = require("@lune/roblox")

local failures = 0
local function fail(message: string)
	failures += 1
	print("  FAIL  " .. message)
end

local environment = {
	Instance = roblox.Instance,
	Vector3 = roblox.Vector3,
	Vector2 = roblox.Vector2,
	CFrame = roblox.CFrame,
	Color3 = roblox.Color3,
	BrickColor = roblox.BrickColor,
	Enum = roblox.Enum,
	typeof = roblox.typeof or typeof,
}

local function loadModule(path: string)
	return luau.load(fs.readFile(path), { debugName = path, environment = environment })()
end

--------------------------------------------------------------------------------
print("1. Compiling every Luau file")
local fileCount = 0
local function walk(directory: string)
	for _, name in fs.readDir(directory) do
		local path = directory .. "/" .. name
		if fs.isDir(path) then
			walk(path)
		elseif string.sub(name, -5) == ".luau" then
			fileCount += 1
			local ok, err = pcall(luau.compile, fs.readFile(path))
			if not ok then
				fail(path .. ": " .. tostring(err))
			end
		end
	end
end
walk("src")
walk("tools")
print(string.format("   %d files", fileCount))

--------------------------------------------------------------------------------
print("2. Checking hero, synergy and config data")
local Config = loadModule("src/shared/Config.luau")
local Heroes = loadModule("src/shared/Heroes.luau")
local Synergies = loadModule("src/shared/Synergies.luau")

if #Config.Teams ~= 2 then
	fail("Config.Teams must have exactly two teams")
end

local WEAPON_KINDS = { Hitscan = true, Projectile = true, Melee = true }
local BOT_USAGES = {
	Engage = true, Attack = true, AttackClose = true, Escape = true,
	Travel = true, Defend = true, HealAllies = true, TeamUlt = true,
}

for _, id in Heroes.Order do
	local hero = Heroes.Definitions[id]
	if not hero then
		fail("Heroes.Order lists '" .. id .. "' but there is no definition")
		continue
	end
	local function need(condition: boolean, what: string)
		if not condition then
			fail(id .. ": " .. what)
		end
	end
	need(hero.Id == id, "Id must match its key")
	need(Heroes.Roles[hero.Role] ~= nil, "unknown Role '" .. tostring(hero.Role) .. "'")
	need((hero.MaxHealth or 0) > 0, "MaxHealth must be > 0")
	need((hero.WalkSpeed or 0) > 0, "WalkSpeed must be > 0")
	need((hero.UltCost or 0) > 0, "UltCost must be > 0")
	need(typeof(hero.Color) == "Color3" and typeof(hero.AccentColor) == "Color3", "Color and AccentColor must be Color3")

	local weapon = hero.Primary or {}
	need(WEAPON_KINDS[weapon.Kind] == true, "Primary.Kind must be Hitscan, Projectile or Melee")
	need((weapon.FireRate or 0) > 0, "Primary.FireRate must be > 0")
	need((weapon.Range or 0) > 0, "Primary.Range must be > 0")
	need((weapon.Damage or 0) > 0, "Primary.Damage must be > 0")
	need(typeof(weapon.TracerColor) == "Color3", "Primary.TracerColor must be a Color3")
	if weapon.Kind == "Projectile" then
		need((weapon.ProjectileSpeed or 0) > 0, "projectile weapons need ProjectileSpeed")
	end
	if weapon.MagazineSize then
		need((weapon.ReloadTime or 0) > 0, "weapons with a magazine need ReloadTime")
	end

	for _, slot in Heroes.AbilitySlots do
		local ability = hero.Abilities and hero.Abilities[slot]
		if not ability then
			fail(id .. ": missing Abilities." .. slot)
		else
			need(type(ability.Name) == "string" and type(ability.Description) == "string", slot .. " needs Name and Description")
			if slot ~= "Ultimate" then
				need((ability.Cooldown or 0) > 0, slot .. ".Cooldown must be > 0")
			end
		end
		local usage = hero.Bot and hero.Bot[slot]
		need(usage == nil or BOT_USAGES[usage] == true, "Bot." .. slot .. " has unknown usage '" .. tostring(usage) .. "'")
	end

	local kitPath = "src/server/HeroKits/" .. id .. ".luau"
	if not fs.isFile(kitPath) then
		fail(id .. ": no kit at " .. kitPath)
	else
		local source = fs.readFile(kitPath)
		for _, slot in Heroes.AbilitySlots do
			need(string.find(source, "function Kit." .. slot .. "(", 1, true) ~= nil, "kit is missing function Kit." .. slot)
		end
	end
end

local SYNERGY_STATS = {
	DamageMult = true, HealingMult = true, CooldownMult = true,
	ProjectileSpeedMult = true, BarrierHealthMult = true, MaxHealthBonus = true,
}
for _, synergy in Synergies.List do
	for _, heroId in synergy.Heroes do
		if not Heroes.Definitions[heroId] then
			fail("synergy " .. synergy.Id .. " lists unknown hero " .. heroId)
		end
	end
	for heroId, bonuses in synergy.Bonuses do
		if not table.find(synergy.Heroes, heroId) then
			fail("synergy " .. synergy.Id .. " gives a bonus to " .. heroId .. ", who is not part of it")
		end
		for stat in bonuses do
			if not SYNERGY_STATS[stat] then
				fail("synergy " .. synergy.Id .. " uses unsupported stat " .. stat)
			end
		end
	end
end
print(string.format("   %d heroes, %d synergies", #Heroes.Order, #Synergies.List))

--------------------------------------------------------------------------------
print("3. Building the map")
local MapBuilder = loadModule("src/server/Map/MapBuilder.luau")
local map = MapBuilder.Build(Config.Teams)
local point = map:FindFirstChild("ControlPoint")
if not point or type(point:GetAttribute("Radius")) ~= "number" then
	fail("map needs a ControlPoint part with a Radius attribute")
end
for _, team in Config.Teams do
	for _, folderName in { "Spawns", "SpawnRooms", "SpawnDoors" } do
		local folder = map:FindFirstChild(folderName)
		if not folder or not folder:FindFirstChild(team.Name) then
			fail("map is missing " .. folderName .. "/" .. team.Name)
		end
	end
end
local destructibles = 0
for _, descendant in map:GetDescendants() do
	if descendant:IsA("BasePart") and descendant:HasTag("Destructible") then
		destructibles += 1
	end
end
print(string.format("   %d destructible props", destructibles))

--------------------------------------------------------------------------------
if failures > 0 then
	print(string.format("\n%d problem(s) found", failures))
	process.exit(1)
end
print("\nAll checks passed")
