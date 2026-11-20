--[[
	HeroSelectController
	====================
	The hero select screen. It opens by itself when you need a hero (start of a match, or joining
	mid-match), and any time you press H. Pick a card, read its kit, press LOCK IN.
	The server decides if the swap happens right away (setup phase, in your spawn room, or while
	dead) or tells you to go back to spawn.
]]

local ContextActionService = game:GetService("ContextActionService")
local GuiService = game:GetService("GuiService")
local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local UserInputService = game:GetService("UserInputService")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Heroes = require(Shared.Heroes)
local Synergies = require(Shared.Synergies)
local Remotes = require(Shared.Remotes)

local ClientState = require(script.Parent.ClientState)
local UIKit = require(script.Parent.UIKit)

local player = Players.LocalPlayer
local Colors = UIKit.Colors
local Fonts = UIKit.Fonts

local HeroSelectController = {}

local gui: ScreenGui
local cards = {}
local detail = {}
local selectedId = nil
local pendingHero = nil
local dismissedThisPhase = false
local teamLabel, timerLabel, lockButton

local function currentHero(): string
	local profile = ClientState.GetProfile()
	return if profile then profile:GetAttribute("Hero") or "" else ""
end

local function selectHero(heroId: string)
	local hero = Heroes.Get(heroId)
	if not hero then
		return
	end
	selectedId = heroId
	for id, refs in cards do
		local isSelected = id == heroId
		refs.Stroke.Color = if isSelected then Heroes.Get(id).AccentColor else Colors.Stroke
		refs.Stroke.Thickness = if isSelected then 3 else 1
	end

	local role = Heroes.Roles[hero.Role]
	detail.Name.Text = string.upper(hero.DisplayName)
	detail.Name.TextColor3 = hero.AccentColor
	detail.Role.Text = role.Icon .. "  " .. role.Name .. "   •   " .. hero.Tagline
	detail.Role.TextColor3 = role.Color
	detail.Description.Text = hero.Description
	detail.Stats.Text = string.format("HEALTH %d   •   SPEED %s", hero.MaxHealth, tostring(hero.WalkSpeed))

	local rows = {
		{ Config.KeyLabels.Fire, hero.Primary.Name, hero.Primary.Description },
		{ Config.KeyLabels.Ability1, hero.Abilities.Ability1.Name, hero.Abilities.Ability1.Description },
		{ Config.KeyLabels.Ability2, hero.Abilities.Ability2.Name, hero.Abilities.Ability2.Description },
		{ Config.KeyLabels.Ultimate, hero.Abilities.Ultimate.Name .. "  (ULTIMATE)", hero.Abilities.Ultimate.Description },
	}
	for index, row in rows do
		local refs = detail.Rows[index]
		refs.Key.Text = row[1]
		refs.Title.Text = row[2]
		refs.Title.TextColor3 = if index == 4 then hero.AccentColor else Colors.Text
		refs.Body.Text = row[3]
	end

	local synergyLines = {}
	for _, synergy in Synergies.ForHero(heroId) do
		local partners = {}
		for _, other in synergy.Heroes do
			if other ~= heroId then
				local otherHero = Heroes.Get(other)
				table.insert(partners, if otherHero then otherHero.DisplayName else other)
			end
		end
		table.insert(synergyLines, string.format("◆ %s (with %s): %s", synergy.Name, table.concat(partners, ", "), synergy.Description))
	end
	detail.Synergies.Text = table.concat(synergyLines, "\n")

	lockButton.BackgroundColor3 = hero.AccentColor
	lockButton.TextColor3 = Color3.fromRGB(14, 16, 24)
	lockButton.Text = if currentHero() == heroId then "PLAYING" else "LOCK IN"
end

local function refreshTeamList()
	local myTeam = ClientState.GetMyTeam()
	local lines = {}
	for _, folder in ClientState.Combatants:GetChildren() do
		if myTeam and folder:GetAttribute("Team") == myTeam then
			local hero = Heroes.Get(folder:GetAttribute("Hero"))
			local name = if folder == ClientState.GetProfile() then "You" else tostring(folder:GetAttribute("DisplayName"))
			table.insert(lines, name .. " — " .. (if hero then hero.DisplayName else "choosing…"))
		end
	end
	table.sort(lines)
	teamLabel.Text = "YOUR TEAM:   " .. table.concat(lines, "    •    ")
end

local function open()
	if gui.Enabled or not ClientState.GetMyTeam() then
		return
	end
	gui.Enabled = true
	ClientState.SetMenu("HeroSelect", true)
	local current = currentHero()
	selectHero(if current ~= "" then current else (selectedId or Heroes.Order[1]))
	refreshTeamList()
	if UserInputService.GamepadEnabled and selectedId and cards[selectedId] then
		GuiService.SelectedObject = cards[selectedId].Button
	end
end

local function close()
	if not gui.Enabled then
		return
	end
	gui.Enabled = false
	pendingHero = nil
	ClientState.SetMenu("HeroSelect", false)
	if GuiService.SelectedObject and GuiService.SelectedObject:IsDescendantOf(gui) then
		GuiService.SelectedObject = nil
	end
end

local function lockIn()
	if not selectedId then
		return
	end
	if currentHero() == selectedId then
		close()
		return
	end
	pendingHero = selectedId
	lockButton.Text = "LOCKING IN…"
	Remotes.SelectHero:FireServer(selectedId)
	local requested = selectedId
	task.delay(1.5, function()
		-- If the server said no (e.g. "return to spawn"), let the player try again.
		if pendingHero == requested and gui.Enabled then
			pendingHero = nil
			lockButton.Text = "LOCK IN"
		end
	end)
end

local function shouldAutoOpen(): boolean
	local profile = ClientState.GetProfile()
	local phase = ClientState.GetPhase()
	local inMatch = phase == "Setup" or phase == "Battle" or phase == "RoundEnd"
	return profile ~= nil and inMatch and currentHero() == "" and ClientState.GetMyTeam() ~= nil
end

local function evaluate()
	if shouldAutoOpen() and not dismissedThisPhase then
		open()
	end
	if gui.Enabled and pendingHero and currentHero() == pendingHero then
		close()
	end
end

--------------------------------------------------------------------------------
-- Building
--------------------------------------------------------------------------------

local function buildCard(parent: Instance, hero, order: number)
	local role = Heroes.Roles[hero.Role]
	local button = UIKit.Button({
		Name = hero.Id,
		Size = UDim2.fromOffset(196, 196),
		BackgroundColor3 = Colors.Panel,
		Text = "",
		LayoutOrder = order,
		Parent = parent,
	})
	UIKit.Corner(button, 12)
	local stroke = UIKit.Stroke(button, Colors.Stroke, 1)

	-- White background: a UIGradient multiplies with it, so the gradient shows the true hero colours.
	local band = UIKit.Frame({ Size = UDim2.new(1, 0, 0, 100), BackgroundColor3 = Color3.new(1, 1, 1), Parent = button })
	UIKit.Corner(band, 12)
	UIKit.Create("UIGradient", {
		Color = ColorSequence.new(hero.Color, hero.AccentColor),
		Rotation = 35,
		Parent = band,
	})
	UIKit.Text({ Size = UDim2.fromScale(1, 1), Font = Fonts.Black, TextSize = 72, TextTransparency = 0.1, Text = string.sub(hero.DisplayName, 1, 1), Parent = band })
	UIKit.Text({
		Position = UDim2.fromOffset(10, 8),
		Size = UDim2.fromOffset(150, 14),
		TextXAlignment = Enum.TextXAlignment.Left,
		TextSize = 11,
		Font = Fonts.Black,
		Text = role.Icon .. " " .. role.Name,
		Parent = band,
	})
	UIKit.Text({ Position = UDim2.fromOffset(0, 106), Size = UDim2.new(1, 0, 0, 26), Font = Fonts.Black, TextSize = 22, Text = string.upper(hero.DisplayName), Parent = button })
	UIKit.Text({
		Position = UDim2.fromOffset(10, 134),
		Size = UDim2.new(1, -20, 0, 30),
		TextSize = 12,
		TextColor3 = Colors.Muted,
		TextWrapped = true,
		Font = Fonts.Medium,
		Text = hero.Tagline,
		Parent = button,
	})
	UIKit.Text({ Position = UDim2.fromOffset(0, 168), Size = UDim2.new(1, 0, 0, 18), TextSize = 12, TextColor3 = role.Color, Text = hero.MaxHealth .. " HP", Parent = button })

	button.Activated:Connect(function()
		selectHero(hero.Id)
	end)
	cards[hero.Id] = { Button = button, Stroke = stroke }
end

local function build(playerGui: PlayerGui)
	gui = UIKit.Create("ScreenGui", {
		Name = "HeroSelect",
		ResetOnSpawn = false,
		IgnoreGuiInset = true,
		DisplayOrder = 10,
		Enabled = false,
		ZIndexBehavior = Enum.ZIndexBehavior.Sibling,
		Parent = playerGui,
	})
	UIKit.Frame({ Size = UDim2.fromScale(1, 1), BackgroundColor3 = Color3.fromRGB(6, 8, 14), BackgroundTransparency = 0.18, Parent = gui })

	local root = UIKit.Frame({
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.fromScale(0.5, 0.5),
		Size = UDim2.fromOffset(1100, 660),
		BackgroundTransparency = 1,
		Parent = gui,
	})
	UIKit.AutoScale(root)

	UIKit.Text({ Size = UDim2.fromOffset(600, 18), TextXAlignment = Enum.TextXAlignment.Left, TextSize = 14, TextColor3 = Colors.Muted, Text = Config.GameName, Parent = root })
	UIKit.Text({ Position = UDim2.fromOffset(0, 18), Size = UDim2.fromOffset(700, 40), TextXAlignment = Enum.TextXAlignment.Left, Font = Fonts.Black, TextSize = 36, Text = "CHOOSE YOUR HERO", Parent = root })
	timerLabel = UIKit.Text({ AnchorPoint = Vector2.new(1, 0), Position = UDim2.new(1, -56, 0, 22), Size = UDim2.fromOffset(400, 32), TextXAlignment = Enum.TextXAlignment.Right, Font = Fonts.Black, TextSize = 22, TextColor3 = Colors.Gold, Parent = root })
	local closeButton = UIKit.Button({ AnchorPoint = Vector2.new(1, 0), Position = UDim2.new(1, 0, 0, 18), Size = UDim2.fromOffset(44, 44), Text = "✕", TextSize = 22, Parent = root })
	UIKit.Corner(closeButton, 8)
	closeButton.Activated:Connect(function()
		dismissedThisPhase = true
		close()
	end)

	local row = UIKit.Frame({ Position = UDim2.fromOffset(0, 74), Size = UDim2.new(1, 0, 0, 196), BackgroundTransparency = 1, Parent = root })
	UIKit.Create("UIListLayout", {
		FillDirection = Enum.FillDirection.Horizontal,
		HorizontalAlignment = Enum.HorizontalAlignment.Center,
		SortOrder = Enum.SortOrder.LayoutOrder,
		Padding = UDim.new(0, 14),
		Parent = row,
	})
	for index, hero in Heroes.GetOrdered() do
		buildCard(row, hero, index)
	end

	local panel = UIKit.Frame({ Position = UDim2.fromOffset(0, 284), Size = UDim2.new(1, 0, 0, 300), BackgroundColor3 = Colors.Panel, BackgroundTransparency = 0.12, Parent = root })
	UIKit.Corner(panel, 12)
	UIKit.Stroke(panel, Colors.Stroke, 1, 0.4)
	detail.Name = UIKit.Text({ Position = UDim2.fromOffset(24, 18), Size = UDim2.fromOffset(420, 40), TextXAlignment = Enum.TextXAlignment.Left, Font = Fonts.Black, TextSize = 38, Parent = panel })
	detail.Role = UIKit.Text({ Position = UDim2.fromOffset(24, 60), Size = UDim2.fromOffset(420, 18), TextXAlignment = Enum.TextXAlignment.Left, TextSize = 13, Parent = panel })
	detail.Description = UIKit.Text({
		Position = UDim2.fromOffset(24, 88),
		Size = UDim2.fromOffset(420, 64),
		TextXAlignment = Enum.TextXAlignment.Left,
		TextYAlignment = Enum.TextYAlignment.Top,
		TextWrapped = true,
		Font = Fonts.Medium,
		TextSize = 15,
		Parent = panel,
	})
	detail.Stats = UIKit.Text({ Position = UDim2.fromOffset(24, 156), Size = UDim2.fromOffset(420, 18), TextXAlignment = Enum.TextXAlignment.Left, TextSize = 13, TextColor3 = Colors.Muted, Parent = panel })
	detail.Synergies = UIKit.Text({
		Position = UDim2.fromOffset(24, 184),
		Size = UDim2.fromOffset(420, 100),
		TextXAlignment = Enum.TextXAlignment.Left,
		TextYAlignment = Enum.TextYAlignment.Top,
		TextWrapped = true,
		Font = Fonts.Medium,
		TextSize = 12,
		TextColor3 = Colors.Gold,
		Parent = panel,
	})

	detail.Rows = {}
	for index = 1, 4 do
		local y = 18 + (index - 1) * 68
		local key = UIKit.Text({
			Position = UDim2.fromOffset(470, y),
			Size = UDim2.fromOffset(64, 26),
			BackgroundTransparency = 0,
			BackgroundColor3 = Colors.PanelLight,
			Font = Fonts.Black,
			TextSize = 12,
			Parent = panel,
		})
		UIKit.Corner(key, 6)
		local title = UIKit.Text({ Position = UDim2.fromOffset(548, y), Size = UDim2.fromOffset(520, 22), TextXAlignment = Enum.TextXAlignment.Left, Font = Fonts.Black, TextSize = 17, Parent = panel })
		local body = UIKit.Text({
			Position = UDim2.fromOffset(548, y + 24),
			Size = UDim2.fromOffset(520, 36),
			TextXAlignment = Enum.TextXAlignment.Left,
			TextYAlignment = Enum.TextYAlignment.Top,
			TextWrapped = true,
			Font = Fonts.Medium,
			TextSize = 13,
			TextColor3 = Colors.Muted,
			Parent = panel,
		})
		detail.Rows[index] = { Key = key, Title = title, Body = body }
	end

	teamLabel = UIKit.Text({
		Position = UDim2.fromOffset(0, 600),
		Size = UDim2.new(1, -260, 0, 44),
		TextXAlignment = Enum.TextXAlignment.Left,
		TextWrapped = true,
		TextSize = 13,
		TextColor3 = Colors.Muted,
		Parent = root,
	})
	lockButton = UIKit.Button({
		AnchorPoint = Vector2.new(1, 0),
		Position = UDim2.new(1, 0, 0, 596),
		Size = UDim2.fromOffset(240, 56),
		TextSize = 24,
		Text = "LOCK IN",
		Parent = root,
	})
	UIKit.Corner(lockButton, 10)
	lockButton.Activated:Connect(lockIn)
end

--------------------------------------------------------------------------------

function HeroSelectController.Init()
	build(player:WaitForChild("PlayerGui"))

	-- H toggles the screen (phones and tablets get an on-screen "Hero" button).
	ContextActionService:BindAction("HS_HeroSelect", function(_, state)
		if state == Enum.UserInputState.Begin then
			if gui.Enabled then
				dismissedThisPhase = true
				close()
			else
				open()
			end
		end
		return Enum.ContextActionResult.Pass
	end, true, table.unpack(Config.Controls.HeroSelect))
	ContextActionService:SetTitle("HS_HeroSelect", "Hero")

	ClientState.MatchState:GetAttributeChangedSignal("Phase"):Connect(function()
		dismissedThisPhase = false
		evaluate()
	end)

	local function watchProfile(profile: Instance)
		if profile.Name ~= "P" .. player.UserId then
			return
		end
		profile:GetAttributeChangedSignal("Hero"):Connect(function()
			if not gui.Enabled then
				dismissedThisPhase = false
			end
			evaluate()
			if gui.Enabled and selectedId then
				selectHero(selectedId)
			end
		end)
		profile:GetAttributeChangedSignal("Team"):Connect(evaluate)
		evaluate()
	end
	ClientState.Combatants.ChildAdded:Connect(watchProfile)
	local existing = ClientState.GetProfile()
	if existing then
		watchProfile(existing)
	end

	local lastRefresh = 0
	RunService.RenderStepped:Connect(function()
		if not gui.Enabled then
			return
		end
		local phase = ClientState.GetPhase()
		local timeLeft = ClientState.TimeLeft()
		timerLabel.Text = if phase == "Setup" and timeLeft then "DOORS OPEN IN " .. UIKit.FormatTime(timeLeft) else ""
		if os.clock() - lastRefresh > 0.5 then
			lastRefresh = os.clock()
			refreshTeamList()
		end
	end)
end

return HeroSelectController
