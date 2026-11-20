--[[
	ScoreboardController
	====================
	Hold TAB (gamepad: press Select) to see both teams: hero, kills, deaths, assists, damage, healing.
	Reads the profile folders the server keeps in ReplicatedStorage.Combatants.
]]

local ContextActionService = game:GetService("ContextActionService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Heroes = require(Shared.Heroes)

local ClientState = require(script.Parent.ClientState)
local UIKit = require(script.Parent.UIKit)

local player = Players.LocalPlayer
local Colors = UIKit.Colors
local Fonts = UIKit.Fonts

local ScoreboardController = {}

local COLUMNS = {
	{ Title = "PLAYER", Width = 250, Align = Enum.TextXAlignment.Left },
	{ Title = "HERO", Width = 130, Align = Enum.TextXAlignment.Left },
	{ Title = "K", Width = 60 },
	{ Title = "D", Width = 60 },
	{ Title = "A", Width = 60 },
	{ Title = "DAMAGE", Width = 100 },
	{ Title = "HEALING", Width = 100 },
}
local ROW_HEIGHT = 30

local gui: ScreenGui
local title: TextLabel
local sections = {}
local refreshing = false

local function makeRow(parent: Instance, values, color: Color3, highlight: boolean, order: number)
	local row = UIKit.Frame({
		Size = UDim2.new(1, 0, 0, ROW_HEIGHT),
		BackgroundColor3 = if highlight then Colors.PanelLight else Colors.Panel,
		BackgroundTransparency = if highlight then 0.1 else 0.5,
		LayoutOrder = order,
		Parent = parent,
	})
	UIKit.Corner(row, 6)
	local x = 12
	for index, column in COLUMNS do
		UIKit.Text({
			Position = UDim2.fromOffset(x, 0),
			Size = UDim2.fromOffset(column.Width, ROW_HEIGHT),
			TextXAlignment = column.Align or Enum.TextXAlignment.Center,
			Text = values[index],
			TextColor3 = if index == 1 then color else Colors.Text,
			Font = if index == 1 then Fonts.Black else Fonts.Bold,
			TextSize = 14,
			TextTruncate = Enum.TextTruncate.AtEnd,
			Parent = row,
		})
		x += column.Width
	end
	return row
end

local function refresh()
	local myTeam = ClientState.GetMyTeam() or Config.Teams[1].Name
	local order = { myTeam, Config.GetOtherTeam(myTeam) or Config.Teams[2].Name }
	local round = ClientState.MatchState:GetAttribute("Round") or 0
	title.Text = Config.GameName .. (if round > 0 then "   •   ROUND " .. round else "")

	for index, team in order do
		local section = sections[index]
		local color = Config.GetTeamColor(team)
		section.Header.Text = (if team == myTeam then "YOUR TEAM" else "ENEMY TEAM") .. "  —  " .. (Config.GetTeam(team) or { DisplayName = team }).DisplayName
		section.Header.TextColor3 = color
		for _, child in section.List:GetChildren() do
			if child:IsA("Frame") then
				child:Destroy()
			end
		end

		local members = {}
		for _, folder in ClientState.Combatants:GetChildren() do
			if folder:GetAttribute("Team") == team then
				table.insert(members, folder)
			end
		end
		table.sort(members, function(a, b)
			return (a:GetAttribute("Kills") or 0) > (b:GetAttribute("Kills") or 0)
		end)
		for position, folder in members do
			local hero = Heroes.Get(folder:GetAttribute("Hero"))
			makeRow(section.List, {
				tostring(folder:GetAttribute("DisplayName")),
				if hero then hero.DisplayName else "—",
				tostring(folder:GetAttribute("Kills") or 0),
				tostring(folder:GetAttribute("Deaths") or 0),
				tostring(folder:GetAttribute("Assists") or 0),
				tostring(math.floor(folder:GetAttribute("Damage") or 0)),
				tostring(math.floor(folder:GetAttribute("Healing") or 0)),
			}, color, folder == ClientState.GetProfile(), position)
		end
	end
end

-- Not a "menu": you can keep aiming and shooting while you peek at the scores.
local function setOpen(open: boolean)
	gui.Enabled = open
	if open and not refreshing then
		refreshing = true
		task.spawn(function()
			while gui.Enabled do
				refresh()
				task.wait(0.5)
			end
			refreshing = false
		end)
	end
end

local function build(playerGui: PlayerGui)
	gui = UIKit.Create("ScreenGui", { Name = "Scoreboard", ResetOnSpawn = false, DisplayOrder = 5, Enabled = false, Parent = playerGui })
	local panel = UIKit.Frame({
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.fromScale(0.5, 0.5),
		Size = UDim2.fromOffset(800, 560),
		BackgroundColor3 = Colors.Panel,
		BackgroundTransparency = 0.08,
		Parent = gui,
	})
	UIKit.Corner(panel, 12)
	UIKit.Stroke(panel, Colors.Stroke, 1, 0.4)
	UIKit.AutoScale(panel)

	title = UIKit.Text({ Position = UDim2.fromOffset(20, 12), Size = UDim2.new(1, -40, 0, 30), TextXAlignment = Enum.TextXAlignment.Left, Font = Fonts.Black, TextSize = 24, Parent = panel })

	local header = UIKit.Frame({ Position = UDim2.fromOffset(20, 48), Size = UDim2.new(1, -40, 0, 20), BackgroundTransparency = 1, Parent = panel })
	local x = 12
	for _, column in COLUMNS do
		UIKit.Text({
			Position = UDim2.fromOffset(x, 0),
			Size = UDim2.fromOffset(column.Width, 20),
			TextXAlignment = column.Align or Enum.TextXAlignment.Center,
			Text = column.Title,
			TextSize = 12,
			TextColor3 = Colors.Muted,
			Parent = header,
		})
		x += column.Width
	end

	for index = 1, 2 do
		local top = 74 + (index - 1) * 240
		local sectionHeader = UIKit.Text({ Position = UDim2.fromOffset(20, top), Size = UDim2.new(1, -40, 0, 22), TextXAlignment = Enum.TextXAlignment.Left, Font = Fonts.Black, TextSize = 15, Parent = panel })
		local list = UIKit.Frame({ Position = UDim2.fromOffset(20, top + 26), Size = UDim2.new(1, -40, 0, 206), BackgroundTransparency = 1, Parent = panel })
		UIKit.Create("UIListLayout", { SortOrder = Enum.SortOrder.LayoutOrder, Padding = UDim.new(0, 4), Parent = list })
		sections[index] = { Header = sectionHeader, List = list }
	end
end

function ScoreboardController.Init()
	build(player:WaitForChild("PlayerGui"))
	ContextActionService:BindAction("HS_Scoreboard", function(_, state, input)
		local isGamepad = input.KeyCode == Enum.KeyCode.ButtonSelect
		if state == Enum.UserInputState.Begin then
			setOpen(if isGamepad then not gui.Enabled else true)
		elseif state == Enum.UserInputState.End and not isGamepad then
			setOpen(false)
		end
		return Enum.ContextActionResult.Pass
	end, false, table.unpack(Config.Controls.Scoreboard))
end

return ScoreboardController
