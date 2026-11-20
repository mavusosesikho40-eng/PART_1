--[[
	Server entry point
	==================
	Configures a few engine settings, then starts every service in dependency order.
	Game logic lives in ./Services, hero abilities in ./HeroKits, the arena in ./Map.
]]

local Players = game:GetService("Players")
local PhysicsService = game:GetService("PhysicsService")
local StarterPlayer = game:GetService("StarterPlayer")

-- 1) Characters are spawned by the game (after you pick a hero), not automatically.
Players.CharacterAutoLoads = false

-- 2) Shift is an ability key, so turn off Roblox's shift-lock toggle.
StarterPlayer.EnableMouseLockOption = false

-- 3) No slow automatic health regeneration: an empty Script named "Health" in
--    StarterCharacterScripts replaces Roblox's default one. Heal at your spawn or with a support!
if not StarterPlayer.StarterCharacterScripts:FindFirstChild("Health") then
	local noRegen = Instance.new("Script")
	noRegen.Name = "Health"
	noRegen.Parent = StarterPlayer.StarterCharacterScripts
end

-- 4) Debris from destroyed cover never bumps into heroes.
for _, group in { "Characters", "Debris" } do
	if not PhysicsService:IsCollisionGroupRegistered(group) then
		PhysicsService:RegisterCollisionGroup(group)
	end
end
PhysicsService:CollisionGroupSetCollidable("Characters", "Debris", false)
PhysicsService:CollisionGroupSetCollidable("Debris", "Debris", false)

local Services = script.Parent:WaitForChild("Services")
local MapService = require(Services.MapService)
local DestructibleService = require(Services.DestructibleService)
local SynergyService = require(Services.SynergyService)
local WeaponService = require(Services.WeaponService)
local AbilityService = require(Services.AbilityService)
local BotService = require(Services.BotService)
local MatchService = require(Services.MatchService)

MapService.Init()
DestructibleService.Init()
SynergyService.Init()
WeaponService.Init()
AbilityService.Init()
BotService.Init()
MatchService.Start()

print("[Rift Arena] Server ready")
