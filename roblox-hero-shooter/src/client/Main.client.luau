--[[
	Client entry point
	==================
	Hides the default Roblox UI we replace, then starts each controller in ./Controllers.
]]

local StarterGui = game:GetService("StarterGui")

local Controllers = script.Parent:WaitForChild("Controllers")

-- Our own scoreboard uses Tab, and our HUD shows health; heroes don't use the backpack.
for _, coreGui in { Enum.CoreGuiType.PlayerList, Enum.CoreGuiType.Health, Enum.CoreGuiType.Backpack } do
	pcall(function()
		StarterGui:SetCoreGuiEnabled(coreGui, false)
	end)
end

local FXController = require(Controllers.FXController)
local CameraController = require(Controllers.CameraController)
local CombatController = require(Controllers.CombatController)
local HUDController = require(Controllers.HUDController)
local HeroSelectController = require(Controllers.HeroSelectController)
local ScoreboardController = require(Controllers.ScoreboardController)
local NameplateController = require(Controllers.NameplateController)

FXController.Init()
CameraController.Init()
CombatController.Init()
HUDController.Init()
HeroSelectController.Init()
ScoreboardController.Init()
NameplateController.Init()
