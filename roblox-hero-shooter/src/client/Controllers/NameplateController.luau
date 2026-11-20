--[[
	NameplateController
	===================
	Floating name + health bar over every other fighter, and a coloured outline:
	  allies   blue outline and nameplate visible through walls (so you can find your team)
	  enemies  red outline, only when you can actually see them
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Heroes = require(Shared.Heroes)
local CombatUtil = require(Shared.CombatUtil)

local ClientState = require(script.Parent.ClientState)
local UIKit = require(script.Parent.UIKit)

local player = Players.LocalPlayer

local NameplateController = {}

local plates = {} -- [character] = { Billboard, Highlight, Fill, Name }

local function displayName(character: Model): string
	local profile = ClientState.Combatants:FindFirstChild(tostring(character:GetAttribute("CombatantId")))
	local name = if profile then tostring(profile:GetAttribute("DisplayName")) else character.Name
	local hero = Heroes.Get(character:GetAttribute("Hero"))
	return if hero then string.upper(hero.DisplayName) .. "  " .. name else name
end

local function create(character: Model, playerGui: PlayerGui)
	local head = character:FindFirstChild("Head")
	if not head or not head:IsA("BasePart") then
		return nil
	end
	local billboard = UIKit.Create("BillboardGui", {
		Name = "Nameplate",
		Adornee = head,
		Size = UDim2.fromOffset(150, 34),
		StudsOffset = Vector3.new(0, 2.6, 0),
		LightInfluence = 0,
		MaxDistance = 220,
		ResetOnSpawn = false,
		Parent = playerGui,
	})
	local name = UIKit.Text({
		Size = UDim2.new(1, 0, 0, 18),
		TextSize = 13,
		Font = UIKit.Fonts.Black,
		TextStrokeTransparency = 0.4,
		Parent = billboard,
	})
	local back = UIKit.Frame({
		AnchorPoint = Vector2.new(0.5, 0),
		Position = UDim2.new(0.5, 0, 0, 22),
		Size = UDim2.fromOffset(110, 7),
		BackgroundColor3 = Color3.fromRGB(15, 15, 20),
		BackgroundTransparency = 0.3,
		Parent = billboard,
	})
	UIKit.Corner(back, 3)
	local fill = UIKit.Frame({ Size = UDim2.fromScale(1, 1), Parent = back })
	UIKit.Corner(fill, 3)

	local highlight = UIKit.Create("Highlight", {
		Adornee = character,
		FillTransparency = 1,
		OutlineTransparency = 0.15,
		Parent = character,
	})
	return { Billboard = billboard, Highlight = highlight, Fill = fill, Name = name }
end

local function refresh(playerGui: PlayerGui)
	local myTeam = ClientState.GetMyTeam()
	local seen = {}
	for _, character in CombatUtil.GetAllCharacters() do
		if character ~= player.Character then
			seen[character] = true
			local plate = plates[character] or create(character, playerGui)
			plates[character] = plate
			if plate then
				local team = character:GetAttribute("Team")
				local ally = myTeam ~= nil and team == myTeam
				local color = Config.GetTeamColor(team)
				local humanoid = CombatUtil.GetHumanoid(character)
				local alive = humanoid ~= nil and humanoid.Health > 0

				plate.Billboard.Enabled = alive
				plate.Billboard.AlwaysOnTop = ally
				plate.Name.Text = displayName(character)
				plate.Name.TextColor3 = color
				if humanoid then
					plate.Fill.Size = UDim2.fromScale(math.clamp(humanoid.Health / math.max(1, humanoid.MaxHealth), 0, 1), 1)
				end
				plate.Fill.BackgroundColor3 = color
				plate.Highlight.Enabled = alive
				plate.Highlight.OutlineColor = color
				plate.Highlight.DepthMode = if ally then Enum.HighlightDepthMode.AlwaysOnTop else Enum.HighlightDepthMode.Occluded
			end
		end
	end
	for character, plate in plates do
		if not seen[character] then
			plate.Billboard:Destroy()
			plate.Highlight:Destroy()
			plates[character] = nil
		end
	end
end

function NameplateController.Init()
	local playerGui = player:WaitForChild("PlayerGui")
	task.spawn(function()
		while true do
			local ok, err = pcall(refresh, playerGui)
			if not ok then
				warn("[Nameplates] " .. tostring(err))
			end
			task.wait(0.1)
		end
	end)
end

return NameplateController
