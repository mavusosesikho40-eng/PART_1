--[[
	HUDController
	=============
	Everything on screen during a match:
	  bottom-left    hero name, health and shields, active buffs
	  bottom-centre  ability cooldowns and ultimate charge, active team-ups
	  bottom-right   ammo
	  top-centre     objective: team scores, point status, round wins, phase timer
	  top-right      kill feed
	  centre         crosshair, hit markers, big announcements, death/respawn panel
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local TweenService = game:GetService("TweenService")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Heroes = require(Shared.Heroes)
local Remotes = require(Shared.Remotes)

local ClientState = require(script.Parent.ClientState)
local UIKit = require(script.Parent.UIKit)
local FXController = require(script.Parent.FXController)

local player = Players.LocalPlayer
local Colors = UIKit.Colors
local Fonts = UIKit.Fonts
local WHITE = Color3.new(1, 1, 1)

local HUDController = {}

local ui = {}
local lastKiller = ""
local feedOrder = 0
local bannerToken = 0
local toastToken = 0
local ultFeedToken = 0
local deniedFlash = {} -- [slot] = os.clock() until which the slot flashes red

--------------------------------------------------------------------------------
-- Building
--------------------------------------------------------------------------------

local function buildOverlay(playerGui: PlayerGui)
	-- Full-screen layer whose centre is the true screen centre (where the camera aims).
	local overlay = UIKit.Create("ScreenGui", {
		Name = "HUDOverlay",
		ResetOnSpawn = false,
		IgnoreGuiInset = true,
		ScreenInsets = Enum.ScreenInsets.None,
		DisplayOrder = 0,
		ZIndexBehavior = Enum.ZIndexBehavior.Sibling,
		Parent = playerGui,
	})

	ui.DamageFlash = UIKit.Frame({
		Name = "DamageFlash",
		Size = UDim2.fromScale(1, 1),
		BackgroundColor3 = Color3.fromRGB(255, 40, 40),
		BackgroundTransparency = 1,
		Parent = overlay,
	})

	local crosshair = UIKit.Frame({
		Name = "Crosshair",
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.fromScale(0.5, 0.5),
		Size = UDim2.fromOffset(40, 40),
		BackgroundTransparency = 1,
		Parent = overlay,
	})
	local function tick(position: UDim2, size: UDim2)
		local line = UIKit.Frame({
			AnchorPoint = Vector2.new(0.5, 0.5),
			Position = position,
			Size = size,
			BackgroundColor3 = WHITE,
			Parent = crosshair,
		})
		UIKit.Stroke(line, Color3.new(0, 0, 0), 1, 0.45)
	end
	tick(UDim2.new(0.5, 0, 0.5, -9), UDim2.fromOffset(2, 7))
	tick(UDim2.new(0.5, 0, 0.5, 9), UDim2.fromOffset(2, 7))
	tick(UDim2.new(0.5, -9, 0.5, 0), UDim2.fromOffset(7, 2))
	tick(UDim2.new(0.5, 9, 0.5, 0), UDim2.fromOffset(7, 2))
	tick(UDim2.fromScale(0.5, 0.5), UDim2.fromOffset(2, 2))
	UIKit.AutoScale(crosshair)
	ui.Crosshair = crosshair

	local marker = UIKit.Frame({
		Name = "HitMarker",
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.fromScale(0.5, 0.5),
		Size = UDim2.fromOffset(32, 32),
		BackgroundTransparency = 1,
		Visible = false,
		Parent = overlay,
	})
	ui.HitMarkerArms = {}
	for _, angle in { 45, 135, 225, 315 } do
		local radians = math.rad(angle)
		local arm = UIKit.Frame({
			AnchorPoint = Vector2.new(0.5, 0.5),
			Position = UDim2.new(0.5, math.sin(radians) * 10, 0.5, -math.cos(radians) * 10),
			Size = UDim2.fromOffset(2, 9),
			Rotation = angle,
			BackgroundColor3 = WHITE,
			Parent = marker,
		})
		table.insert(ui.HitMarkerArms, arm)
	end
	UIKit.AutoScale(marker)
	ui.HitMarker = marker

	ui.KillConfirm = UIKit.Text({
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.new(0.5, 0, 0.5, 80),
		Size = UDim2.fromOffset(420, 28),
		Font = Fonts.Black,
		TextSize = 20,
		TextColor3 = Colors.HealthLow,
		TextStrokeTransparency = 0.5,
		Visible = false,
		Parent = overlay,
	})
	UIKit.AutoScale(ui.KillConfirm)
end

local function buildVitals(gui: ScreenGui)
	local panel = UIKit.Frame({
		Name = "Vitals",
		AnchorPoint = Vector2.new(0, 1),
		Position = UDim2.new(0, 24, 1, -24),
		Size = UDim2.fromOffset(340, 110),
		BackgroundTransparency = 1,
		Parent = gui,
	})
	UIKit.AutoScale(panel)
	ui.Vitals = panel

	ui.Effects = UIKit.Text({ Size = UDim2.new(1, 0, 0, 16), TextXAlignment = Enum.TextXAlignment.Left, TextSize = 12, TextColor3 = Colors.Gold, Parent = panel })
	ui.HeroName = UIKit.Text({ Position = UDim2.fromOffset(0, 18), Size = UDim2.new(1, -110, 0, 30), TextXAlignment = Enum.TextXAlignment.Left, Font = Fonts.Black, TextSize = 28, Parent = panel })
	ui.Role = UIKit.Text({ Position = UDim2.fromOffset(0, 48), Size = UDim2.new(1, -110, 0, 16), TextXAlignment = Enum.TextXAlignment.Left, TextSize = 13, Parent = panel })
	ui.HealthNumber = UIKit.Text({ AnchorPoint = Vector2.new(1, 0), Position = UDim2.new(1, -66, 0, 18), Size = UDim2.fromOffset(110, 34), TextXAlignment = Enum.TextXAlignment.Right, Font = Fonts.Black, TextSize = 32, Parent = panel })
	ui.HealthMax = UIKit.Text({ AnchorPoint = Vector2.new(1, 0), Position = UDim2.new(1, -20, 0, 30), Size = UDim2.fromOffset(46, 18), TextXAlignment = Enum.TextXAlignment.Left, TextSize = 14, TextColor3 = Colors.Muted, Parent = panel })
	ui.ShieldText = UIKit.Text({ AnchorPoint = Vector2.new(1, 0), Position = UDim2.new(1, -20, 0, 50), Size = UDim2.fromOffset(150, 16), TextXAlignment = Enum.TextXAlignment.Right, TextSize = 12, TextColor3 = Colors.Shield, Parent = panel })

	local shieldBack = UIKit.Frame({ Position = UDim2.fromOffset(0, 70), Size = UDim2.fromOffset(320, 6), BackgroundColor3 = Colors.Panel, BackgroundTransparency = 0.4, Parent = panel })
	UIKit.Corner(shieldBack, 3)
	ui.ShieldBack = shieldBack
	ui.ShieldFill = UIKit.Frame({ Size = UDim2.fromScale(0, 1), BackgroundColor3 = Colors.Shield, Parent = shieldBack })
	UIKit.Corner(ui.ShieldFill, 3)

	local healthBack = UIKit.Frame({ Position = UDim2.fromOffset(0, 80), Size = UDim2.fromOffset(320, 24), BackgroundColor3 = Colors.Panel, BackgroundTransparency = 0.25, Parent = panel })
	UIKit.Corner(healthBack, 6)
	UIKit.Stroke(healthBack, Colors.Stroke, 1, 0.3)
	ui.HealthFill = UIKit.Frame({ Size = UDim2.fromScale(1, 1), BackgroundColor3 = Colors.Health, Parent = healthBack })
	UIKit.Corner(ui.HealthFill, 6)
end

local function buildAbilities(gui: ScreenGui)
	local boxSize, gap = 80, 14
	local width = boxSize * 3 + gap * 2
	local panel = UIKit.Frame({
		Name = "Abilities",
		AnchorPoint = Vector2.new(0.5, 1),
		Position = UDim2.new(0.5, 0, 1, -40),
		Size = UDim2.fromOffset(width, boxSize + 24),
		BackgroundTransparency = 1,
		Parent = gui,
	})
	UIKit.AutoScale(panel)
	ui.Abilities = panel

	ui.Synergy = UIKit.Text({ Size = UDim2.new(1, 0, 0, 18), TextSize = 13, TextColor3 = Colors.Gold, Parent = panel })
	ui.Toast = UIKit.Text({
		AnchorPoint = Vector2.new(0.5, 1),
		Position = UDim2.new(0.5, 0, 0, -6),
		Size = UDim2.fromOffset(600, 22),
		TextSize = 16,
		TextColor3 = Colors.Gold,
		TextStrokeTransparency = 0.5,
		Visible = false,
		Parent = panel,
	})

	ui.Slots = {}
	for index, slot in Heroes.AbilitySlots do
		local box = UIKit.Frame({
			Name = slot,
			Position = UDim2.fromOffset((index - 1) * (boxSize + gap), 24),
			Size = UDim2.fromOffset(boxSize, boxSize),
			BackgroundColor3 = Colors.Panel,
			BackgroundTransparency = 0.2,
			Parent = panel,
		})
		UIKit.Corner(box, 10)
		local stroke = UIKit.Stroke(box, Colors.Stroke, 2)
		local fill = UIKit.Frame({
			Name = "Charge",
			AnchorPoint = Vector2.new(0, 1),
			Position = UDim2.fromScale(0, 1),
			Size = UDim2.fromScale(1, 0),
			BackgroundColor3 = Colors.Gold,
			BackgroundTransparency = 0.55,
			Parent = box,
		})
		UIKit.Corner(fill, 10)
		local icon = UIKit.Text({ Position = UDim2.fromOffset(0, 6), Size = UDim2.new(1, 0, 1, -12), Font = Fonts.Black, TextSize = 34, ZIndex = 2, Parent = box })
		UIKit.Text({
			Position = UDim2.fromOffset(6, 4),
			Size = UDim2.fromOffset(70, 14),
			TextXAlignment = Enum.TextXAlignment.Left,
			TextSize = 11,
			TextColor3 = Colors.Muted,
			Text = Config.KeyLabels[slot] or "",
			ZIndex = 3,
			Parent = box,
		})
		local name = UIKit.Text({
			AnchorPoint = Vector2.new(0.5, 0),
			Position = UDim2.new(0.5, 0, 1, 4),
			Size = UDim2.fromOffset(boxSize + gap - 2, 14),
			TextSize = 10,
			TextScaled = false,
			TextTruncate = Enum.TextTruncate.AtEnd,
			ZIndex = 2,
			Parent = box,
		})
		local cover = UIKit.Frame({ Size = UDim2.fromScale(1, 1), BackgroundColor3 = Color3.new(0, 0, 0), BackgroundTransparency = 0.35, Visible = false, ZIndex = 4, Parent = box })
		UIKit.Corner(cover, 10)
		local count = UIKit.Text({ Size = UDim2.fromScale(1, 1), Font = Fonts.Black, TextSize = 30, ZIndex = 5, Parent = cover })
		ui.Slots[slot] = { Box = box, Stroke = stroke, Fill = fill, Icon = icon, Name = name, Cover = cover, Count = count }
	end
end

local function buildAmmo(gui: ScreenGui)
	local panel = UIKit.Frame({
		Name = "Ammo",
		AnchorPoint = Vector2.new(1, 1),
		Position = UDim2.new(1, -24, 1, -24),
		Size = UDim2.fromOffset(240, 96),
		BackgroundTransparency = 1,
		Parent = gui,
	})
	UIKit.AutoScale(panel)
	ui.Ammo = panel
	ui.Reloading = UIKit.Text({ Position = UDim2.fromOffset(0, 0), Size = UDim2.new(1, 0, 0, 18), TextXAlignment = Enum.TextXAlignment.Right, TextSize = 14, TextColor3 = Colors.Gold, Text = "RELOADING", Visible = false, Parent = panel })
	ui.AmmoCount = UIKit.Text({ Position = UDim2.fromOffset(0, 20), Size = UDim2.new(1, -62, 0, 52), TextXAlignment = Enum.TextXAlignment.Right, Font = Fonts.Black, TextSize = 48, Parent = panel })
	ui.AmmoMax = UIKit.Text({ AnchorPoint = Vector2.new(1, 0), Position = UDim2.new(1, 0, 0, 44), Size = UDim2.fromOffset(58, 24), TextXAlignment = Enum.TextXAlignment.Left, TextSize = 20, TextColor3 = Colors.Muted, Parent = panel })
	ui.WeaponName = UIKit.Text({ Position = UDim2.fromOffset(0, 76), Size = UDim2.new(1, 0, 0, 18), TextXAlignment = Enum.TextXAlignment.Right, TextSize = 13, TextColor3 = Colors.Muted, Parent = panel })
end

local function teamBox(parent: Frame, anchorX: number, position: UDim2)
	local box = UIKit.Frame({
		AnchorPoint = Vector2.new(anchorX, 0),
		Position = position,
		Size = UDim2.fromOffset(190, 46),
		BackgroundColor3 = Colors.Panel,
		BackgroundTransparency = 0.2,
		ClipsDescendants = true,
		Parent = parent,
	})
	UIKit.Corner(box, 8)
	local fill = UIKit.Frame({
		AnchorPoint = Vector2.new(anchorX, 0),
		Position = UDim2.fromScale(anchorX, 0),
		Size = UDim2.fromScale(0, 1),
		BackgroundTransparency = 0.45,
		Parent = box,
	})
	local label = UIKit.Text({ Size = UDim2.fromScale(1, 1), Font = Fonts.Black, TextSize = 20, ZIndex = 2, Parent = box })

	local dotsFrame = UIKit.Frame({
		AnchorPoint = Vector2.new(anchorX, 0),
		Position = UDim2.new(position.X.Scale, position.X.Offset, 0, 52),
		Size = UDim2.fromOffset(190, 10),
		BackgroundTransparency = 1,
		Parent = parent,
	})
	UIKit.Create("UIListLayout", {
		FillDirection = Enum.FillDirection.Horizontal,
		HorizontalAlignment = if anchorX == 0 then Enum.HorizontalAlignment.Left else Enum.HorizontalAlignment.Right,
		Padding = UDim.new(0, 6),
		Parent = dotsFrame,
	})
	local dots = {}
	for index = 1, Config.Match.RoundsToWin do
		local dot = UIKit.Frame({ Size = UDim2.fromOffset(10, 10), BackgroundColor3 = Colors.PanelLight, LayoutOrder = index, Parent = dotsFrame })
		UIKit.Corner(dot, 5)
		table.insert(dots, dot)
	end
	return { Box = box, Fill = fill, Label = label, Dots = dots }
end

local function buildObjective(gui: ScreenGui)
	local panel = UIKit.Frame({
		Name = "Objective",
		AnchorPoint = Vector2.new(0.5, 0),
		Position = UDim2.new(0.5, 0, 0, 8),
		Size = UDim2.fromOffset(560, 116),
		BackgroundTransparency = 1,
		Parent = gui,
	})
	UIKit.AutoScale(panel)

	ui.LeftTeam = teamBox(panel, 0, UDim2.fromOffset(0, 0))
	ui.RightTeam = teamBox(panel, 1, UDim2.new(1, 0, 0, 0))

	local badge = UIKit.Frame({
		AnchorPoint = Vector2.new(0.5, 0),
		Position = UDim2.fromScale(0.5, 0),
		Size = UDim2.fromOffset(150, 46),
		BackgroundColor3 = Colors.Panel,
		Parent = panel,
	})
	UIKit.Corner(badge, 8)
	ui.PointStroke = UIKit.Stroke(badge, Colors.Stroke, 2)
	ui.PointBadge = badge
	ui.PointText = UIKit.Text({ Size = UDim2.fromScale(1, 1), Font = Fonts.Black, TextSize = 17, Text = "POINT", Parent = badge })

	local captureBack = UIKit.Frame({
		AnchorPoint = Vector2.new(0.5, 0),
		Position = UDim2.new(0.5, 0, 0, 52),
		Size = UDim2.fromOffset(150, 6),
		BackgroundColor3 = Colors.Panel,
		BackgroundTransparency = 0.3,
		Parent = panel,
	})
	UIKit.Corner(captureBack, 3)
	ui.CaptureBack = captureBack
	ui.CaptureFill = UIKit.Frame({ Size = UDim2.fromScale(0, 1), Parent = captureBack })
	UIKit.Corner(ui.CaptureFill, 3)

	ui.PhaseText = UIKit.Text({
		AnchorPoint = Vector2.new(0.5, 0),
		Position = UDim2.new(0.5, 0, 0, 66),
		Size = UDim2.fromOffset(560, 22),
		TextSize = 16,
		TextStrokeTransparency = 0.6,
		Parent = panel,
	})
	ui.UltFeed = UIKit.Text({
		AnchorPoint = Vector2.new(0.5, 0),
		Position = UDim2.new(0.5, 0, 0, 92),
		Size = UDim2.fromOffset(560, 20),
		Font = Fonts.Black,
		TextSize = 15,
		TextStrokeTransparency = 0.5,
		Visible = false,
		Parent = panel,
	})
end

local function buildKillFeed(gui: ScreenGui)
	local feed = UIKit.Frame({
		Name = "KillFeed",
		AnchorPoint = Vector2.new(1, 0),
		Position = UDim2.new(1, -16, 0, 8),
		Size = UDim2.fromOffset(400, 220),
		BackgroundTransparency = 1,
		Parent = gui,
	})
	UIKit.Create("UIListLayout", {
		FillDirection = Enum.FillDirection.Vertical,
		HorizontalAlignment = Enum.HorizontalAlignment.Right,
		SortOrder = Enum.SortOrder.LayoutOrder,
		Padding = UDim.new(0, 4),
		Parent = feed,
	})
	UIKit.AutoScale(feed)
	ui.KillFeed = feed
end

local function buildBanner(gui: ScreenGui)
	local banner = UIKit.Frame({
		Name = "Banner",
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.fromScale(0.5, 0.3),
		Size = UDim2.fromOffset(820, 110),
		BackgroundTransparency = 1,
		Visible = false,
		Parent = gui,
	})
	UIKit.AutoScale(banner)
	ui.Banner = banner
	ui.BannerTitle = UIKit.Text({ Size = UDim2.new(1, 0, 0, 70), Font = Fonts.Black, TextSize = 60, TextStrokeTransparency = 0.4, Parent = banner })
	ui.BannerSubtitle = UIKit.Text({ Position = UDim2.fromOffset(0, 72), Size = UDim2.new(1, 0, 0, 28), TextSize = 20, TextStrokeTransparency = 0.6, Parent = banner })
end

local function buildDeathPanel(gui: ScreenGui)
	local panel = UIKit.Frame({
		Name = "DeathPanel",
		AnchorPoint = Vector2.new(0.5, 0.5),
		Position = UDim2.fromScale(0.5, 0.64),
		Size = UDim2.fromOffset(460, 130),
		BackgroundColor3 = Colors.Panel,
		BackgroundTransparency = 0.25,
		Visible = false,
		Parent = gui,
	})
	UIKit.Corner(panel, 12)
	UIKit.AutoScale(panel)
	ui.DeathPanel = panel
	ui.DeathTitle = UIKit.Text({ Position = UDim2.fromOffset(0, 10), Size = UDim2.new(1, 0, 0, 34), Font = Fonts.Black, TextSize = 30, Parent = panel })
	ui.DeathBy = UIKit.Text({ Position = UDim2.fromOffset(0, 46), Size = UDim2.new(1, 0, 0, 20), TextSize = 16, TextColor3 = Colors.Muted, Parent = panel })
	ui.DeathTimer = UIKit.Text({ Position = UDim2.fromOffset(0, 70), Size = UDim2.new(1, 0, 0, 28), Font = Fonts.Black, TextSize = 22, Parent = panel })
	ui.DeathHint = UIKit.Text({ Position = UDim2.fromOffset(0, 102), Size = UDim2.new(1, 0, 0, 18), TextSize = 14, TextColor3 = Colors.Muted, Parent = panel })
end

--------------------------------------------------------------------------------
-- Messages
--------------------------------------------------------------------------------

local function showBanner(title: string, subtitle: string?, color: Color3?, duration: number?)
	bannerToken += 1
	local token = bannerToken
	ui.BannerTitle.Text = title
	ui.BannerTitle.TextColor3 = color or Colors.Text
	ui.BannerSubtitle.Text = subtitle or ""
	ui.BannerTitle.TextTransparency = 1
	ui.BannerSubtitle.TextTransparency = 1
	ui.Banner.Visible = true
	local fadeIn = TweenInfo.new(0.2)
	TweenService:Create(ui.BannerTitle, fadeIn, { TextTransparency = 0 }):Play()
	TweenService:Create(ui.BannerSubtitle, fadeIn, { TextTransparency = 0 }):Play()
	task.delay(duration or 2.5, function()
		if bannerToken ~= token then
			return
		end
		local fade = TweenInfo.new(0.4)
		TweenService:Create(ui.BannerTitle, fade, { TextTransparency = 1 }):Play()
		TweenService:Create(ui.BannerSubtitle, fade, { TextTransparency = 1 }):Play()
		task.wait(0.45)
		if bannerToken == token then
			ui.Banner.Visible = false
		end
	end)
end

local function showToast(text: string)
	toastToken += 1
	local token = toastToken
	ui.Toast.Text = text
	ui.Toast.Visible = true
	task.delay(2.5, function()
		if toastToken == token then
			ui.Toast.Visible = false
		end
	end)
end

local function addKillFeed(data)
	feedOrder += 1
	local entry = UIKit.Frame({
		Size = UDim2.fromOffset(0, 26),
		AutomaticSize = Enum.AutomaticSize.X,
		BackgroundColor3 = Colors.Panel,
		BackgroundTransparency = 0.3,
		LayoutOrder = feedOrder,
		Parent = ui.KillFeed,
	})
	UIKit.Corner(entry, 6)
	UIKit.Create("UIPadding", { PaddingLeft = UDim.new(0, 8), PaddingRight = UDim.new(0, 8), Parent = entry })
	UIKit.Create("UIListLayout", {
		FillDirection = Enum.FillDirection.Horizontal,
		VerticalAlignment = Enum.VerticalAlignment.Center,
		SortOrder = Enum.SortOrder.LayoutOrder,
		Padding = UDim.new(0, 6),
		Parent = entry,
	})
	local function chunk(text: string?, color: Color3, order: number, font: Enum.Font?)
		if not text or text == "" then
			return
		end
		UIKit.Text({
			Size = UDim2.fromOffset(0, 22),
			AutomaticSize = Enum.AutomaticSize.X,
			Text = text,
			TextColor3 = color,
			TextSize = 14,
			Font = font or Fonts.Bold,
			LayoutOrder = order,
			Parent = entry,
		})
	end
	local function heroName(heroId)
		local hero = Heroes.Get(heroId)
		return if hero then hero.DisplayName else nil
	end

	if data.KillerName then
		chunk(data.KillerName, Config.GetTeamColor(data.KillerTeam), 1)
		chunk(heroName(data.KillerHero), Colors.Muted, 2, Fonts.Regular)
		chunk("›", Colors.Text, 3, Fonts.Black)
	else
		chunk("✕", Colors.Muted, 3, Fonts.Black)
	end
	chunk(data.VictimName, Config.GetTeamColor(data.VictimTeam), 4)
	chunk(heroName(data.VictimHero), Colors.Muted, 5, Fonts.Regular)

	-- Keep the newest six.
	local entries = {}
	for _, child in ui.KillFeed:GetChildren() do
		if child:IsA("Frame") then
			table.insert(entries, child)
		end
	end
	table.sort(entries, function(a, b)
		return a.LayoutOrder < b.LayoutOrder
	end)
	for index = 1, #entries - 6 do
		entries[index]:Destroy()
	end
	task.delay(7, function()
		if entry.Parent then
			entry:Destroy()
		end
	end)
end

local function flashHitMarker(color: Color3)
	for _, arm in ui.HitMarkerArms do
		arm.BackgroundColor3 = color
	end
	ui.HitMarker.Visible = true
	local shownAt = os.clock()
	ui.HitMarkerShownAt = shownAt
	task.delay(0.15, function()
		if ui.HitMarkerShownAt == shownAt then
			ui.HitMarker.Visible = false
		end
	end)
end

local function onNotify(kind: string, data)
	if typeof(data) ~= "table" then
		return
	end
	local myTeam = ClientState.GetMyTeam()
	if kind == "Kill" then
		addKillFeed(data)
	elseif kind == "Announce" then
		showBanner(data.Title or "", data.Subtitle, nil, data.Duration)
	elseif kind == "Message" then
		showToast(data.Text or "")
	elseif kind == "PointCaptured" then
		if data.Team == myTeam then
			showBanner("POINT CAPTURED", "Hold it to win the round!", Config.GetTeamColor(data.Team), 2)
		else
			showBanner("POINT LOST", "Take it back!", Colors.HealthLow, 2)
		end
	elseif kind == "RoundOver" then
		local won = data.Winner == myTeam
		showBanner(if won then "ROUND WON" else "ROUND LOST", "Round " .. tostring(data.Round or ""), if won then Config.GetTeamColor(myTeam) else Colors.HealthLow, 4)
	elseif kind == "MatchOver" then
		local won = data.Winner == myTeam
		showBanner(if won then "VICTORY" else "DEFEAT", Config.GameName, if won then Colors.Gold else Colors.HealthLow, 8)
	elseif kind == "Eliminated" then
		local hero = Heroes.Get(data.ByHero)
		if data.By then
			lastKiller = "by " .. data.By .. (if hero then " (" .. hero.DisplayName .. ")" else "")
		else
			lastKiller = ""
		end
	elseif kind == "KillConfirmed" then
		ui.KillConfirm.Text = "ELIMINATED  " .. string.upper(tostring(data.Victim or ""))
		ui.KillConfirm.Visible = true
		local text = ui.KillConfirm.Text
		task.delay(1.6, function()
			if ui.KillConfirm.Text == text then
				ui.KillConfirm.Visible = false
			end
		end)
	end
end

--------------------------------------------------------------------------------
-- Per-frame updates
--------------------------------------------------------------------------------

local function updateVitals(character: Model, humanoid: Humanoid, hero)
	ui.HeroName.Text = string.upper(hero.DisplayName)
	ui.HeroName.TextColor3 = hero.AccentColor
	local role = Heroes.Roles[hero.Role]
	ui.Role.Text = if role then role.Icon .. "  " .. role.Name else ""
	ui.Role.TextColor3 = if role then role.Color else Colors.Muted

	local maxHealth = math.max(1, humanoid.MaxHealth)
	local health = math.clamp(humanoid.Health, 0, maxHealth)
	local fraction = health / maxHealth
	ui.HealthNumber.Text = tostring(math.ceil(health))
	ui.HealthMax.Text = "/" .. tostring(math.floor(maxHealth))
	ui.HealthFill.Size = UDim2.fromScale(fraction, 1)
	ui.HealthFill.BackgroundColor3 = if fraction < 0.35 then Colors.HealthLow else Colors.Health

	local shield = character:GetAttribute("Shield") or 0
	ui.ShieldBack.Visible = shield > 0
	ui.ShieldFill.Size = UDim2.fromScale(math.clamp(shield / maxHealth, 0, 1), 1)
	ui.ShieldText.Text = if shield > 0 then "+" .. tostring(math.ceil(shield)) .. " SHIELD" else ""

	local effects = character:GetAttribute("Effects") or ""
	ui.Effects.Text = string.upper((string.gsub(effects, ",", "  •  ")))
end

local function updateAbilities(character: Model, hero, profile: Folder?, now: number)
	local synergies = if profile then profile:GetAttribute("Synergies") or "" else ""
	ui.Synergy.Text = if synergies ~= "" then "◆ TEAM-UP: " .. string.upper(synergies) else ""

	for _, slot in Heroes.AbilitySlots do
		local refs = ui.Slots[slot]
		local ability = hero.Abilities[slot]
		refs.Name.Text = string.upper(ability.Name)
		local denied = (deniedFlash[slot] or 0) > os.clock()

		if slot == "Ultimate" then
			local charge = if profile then profile:GetAttribute("UltCharge") or 0 else 0
			local ready = charge >= 100
			refs.Fill.Size = UDim2.fromScale(1, math.clamp(charge / 100, 0, 1))
			refs.Fill.BackgroundColor3 = hero.AccentColor
			refs.Cover.Visible = false
			refs.Icon.Text = if ready then (ability.Icon or "✦") else tostring(math.floor(charge)) .. "%"
			refs.Icon.TextSize = if ready then 34 else 22
			refs.Stroke.Color = if denied then Colors.HealthLow elseif ready then hero.AccentColor else Colors.Stroke
			refs.Stroke.Thickness = if ready then 2.5 + math.sin(os.clock() * 6) else 2
		else
			local remaining = (character:GetAttribute("CD_" .. slot) or 0) - now
			local cooling = remaining > 0
			refs.Icon.Text = ability.Icon or "◆"
			refs.Icon.TextColor3 = hero.AccentColor
			refs.Fill.Size = UDim2.fromScale(1, 0)
			refs.Cover.Visible = cooling
			refs.Count.Text = if cooling then tostring(math.ceil(remaining)) else ""
			refs.Stroke.Color = if denied then Colors.HealthLow elseif cooling then Colors.Stroke else hero.AccentColor
			refs.Stroke.Thickness = 2
		end
	end
end

local function updateAmmo(character: Model, hero)
	local weapon = hero.Primary
	ui.WeaponName.Text = string.upper(weapon.Name)
	if weapon.MagazineSize then
		ui.AmmoCount.Text = if character:GetAttribute("InfiniteAmmo") then "∞" else tostring(character:GetAttribute("Ammo") or 0)
		ui.AmmoMax.Text = "/" .. tostring(weapon.MagazineSize)
	else
		ui.AmmoCount.Text = "∞"
		ui.AmmoMax.Text = ""
	end
	ui.Reloading.Visible = character:GetAttribute("Reloading") == true
end

local function updateObjective(phase: string, now: number)
	local state = ClientState.MatchState
	local myTeam = ClientState.GetMyTeam() or Config.Teams[1].Name
	local enemyTeam = Config.GetOtherTeam(myTeam) or Config.Teams[2].Name

	for _, side in { { ui.LeftTeam, myTeam, "YOU" }, { ui.RightTeam, enemyTeam, "ENEMY" } } do
		local refs, team, label = side[1], side[2], side[3]
		local score = state:GetAttribute("Score_" .. team) or 0
		local color = Config.GetTeamColor(team)
		refs.Fill.Size = UDim2.fromScale(math.clamp(score / 100, 0, 1), 1)
		refs.Fill.BackgroundColor3 = color
		refs.Label.Text = label .. "  " .. tostring(math.floor(score)) .. "%"
		refs.Label.TextColor3 = Colors.Text
		local wins = state:GetAttribute("RoundWins_" .. team) or 0
		for index, dot in refs.Dots do
			dot.BackgroundColor3 = if index <= wins then color else Colors.PanelLight
		end
	end

	local owner = state:GetAttribute("PointOwner") or ""
	local locked = state:GetAttribute("PointLocked") ~= false
	local contested = state:GetAttribute("Contested") == true
	local overtime = state:GetAttribute("Overtime") == true
	local battle = phase == "Battle"

	local badgeText = "POINT"
	if battle then
		if locked then
			badgeText = "LOCKED"
		elseif overtime then
			badgeText = "OVERTIME"
		elseif contested then
			badgeText = "CONTESTED"
		elseif owner == myTeam then
			badgeText = "HOLDING"
		elseif owner ~= "" then
			badgeText = "ENEMY HOLDS"
		else
			badgeText = "NEUTRAL"
		end
	end
	ui.PointText.Text = badgeText
	ui.PointBadge.BackgroundColor3 = if owner ~= "" then Config.GetTeamColor(owner):Lerp(Colors.Panel, 0.45) else Colors.Panel
	local flashing = battle and (contested or overtime) and math.sin(os.clock() * 10) > 0
	ui.PointStroke.Color = if flashing then Colors.Gold else Colors.Stroke

	local captureTeam = state:GetAttribute("CaptureTeam") or ""
	local progress = state:GetAttribute("CaptureProgress") or 0
	ui.CaptureBack.Visible = battle and progress > 0
	ui.CaptureFill.Size = UDim2.fromScale(math.clamp(progress / 100, 0, 1), 1)
	ui.CaptureFill.BackgroundColor3 = Config.GetTeamColor(captureTeam)

	local round = state:GetAttribute("Round") or 0
	local timeLeft = ClientState.TimeLeft()
	local text = ""
	if phase == "Waiting" then
		text = "WAITING FOR PLAYERS"
	elseif phase == "Intermission" then
		text = "MATCH STARTS IN " .. UIKit.FormatTime(timeLeft)
	elseif phase == "Setup" then
		text = "ROUND " .. round .. "  •  DOORS OPEN IN " .. UIKit.FormatTime(timeLeft)
	elseif phase == "Battle" then
		if locked then
			text = "POINT UNLOCKS IN " .. UIKit.FormatTime((state:GetAttribute("PointUnlockAt") or 0) - now)
		else
			text = "ROUND " .. round
		end
	elseif phase == "RoundEnd" then
		text = "ROUND OVER"
	elseif phase == "MatchEnd" then
		text = "MATCH OVER  •  NEW MATCH SOON"
	end
	ui.PhaseText.Text = text
end

local function updateDeathPanel(profile: Folder?, phase: string, now: number, alive: boolean)
	local inMatch = phase == "Setup" or phase == "Battle" or phase == "RoundEnd"
	local show = false
	if profile and inMatch and not alive and not ClientState.IsMenuOpen("HeroSelect") then
		show = true
		local hasHero = (profile:GetAttribute("Hero") or "") ~= ""
		local respawnAt = profile:GetAttribute("RespawnAt") or 0
		local hotkey = Config.KeyLabels.HeroSelect
		if not hasHero then
			ui.DeathTitle.Text = "CHOOSE A HERO"
			ui.DeathTitle.TextColor3 = Colors.Text
			ui.DeathBy.Text = ""
			ui.DeathTimer.Text = ""
			ui.DeathHint.Text = "Press " .. hotkey .. " to open hero select"
		elseif respawnAt > now then
			ui.DeathTitle.Text = "ELIMINATED"
			ui.DeathTitle.TextColor3 = Colors.HealthLow
			ui.DeathBy.Text = lastKiller
			ui.DeathTimer.Text = "RESPAWN IN " .. tostring(math.ceil(respawnAt - now))
			ui.DeathHint.Text = "Press " .. hotkey .. " to change hero"
		else
			ui.DeathTitle.Text = "DEPLOYING"
			ui.DeathTitle.TextColor3 = Colors.Text
			ui.DeathBy.Text = ""
			ui.DeathTimer.Text = if phase == "RoundEnd" then "NEXT ROUND SOON" else ""
			ui.DeathHint.Text = "Press " .. hotkey .. " to change hero"
		end
	end
	ui.DeathPanel.Visible = show
end

local function update()
	local profile = ClientState.GetProfile()
	local character = player.Character
	local humanoid = character and character:FindFirstChildOfClass("Humanoid")
	local alive = humanoid ~= nil and humanoid.Health > 0
	local hero = character and Heroes.Get(character:GetAttribute("Hero"))
	local phase = ClientState.GetPhase()
	local now = workspace:GetServerTimeNow()

	local showCombat = alive and hero ~= nil
	ui.Vitals.Visible = showCombat
	ui.Abilities.Visible = showCombat
	ui.Ammo.Visible = showCombat
	ui.Crosshair.Visible = showCombat and not ClientState.IsMenuOpen()

	if showCombat then
		updateVitals(character, humanoid, hero)
		updateAbilities(character, hero, profile, now)
		updateAmmo(character, hero)
	end
	updateObjective(phase, now)
	updateDeathPanel(profile, phase, now, alive)
end

--------------------------------------------------------------------------------

function HUDController.Init()
	local playerGui = player:WaitForChild("PlayerGui")
	buildOverlay(playerGui)

	local gui = UIKit.Create("ScreenGui", {
		Name = "HUD",
		ResetOnSpawn = false,
		DisplayOrder = 1,
		ZIndexBehavior = Enum.ZIndexBehavior.Sibling,
		Parent = playerGui,
	})
	buildVitals(gui)
	buildAbilities(gui)
	buildAmmo(gui)
	buildObjective(gui)
	buildKillFeed(gui)
	buildBanner(gui)
	buildDeathPanel(gui)

	RunService.RenderStepped:Connect(update)

	Remotes.Notify.OnClientEvent:Connect(onNotify)

	Remotes.HitConfirm.OnClientEvent:Connect(function(data)
		if typeof(data) ~= "table" then
			return
		end
		local amount = tostring(data.Amount or 0)
		if data.Heal then
			if data.Position then
				FXController.DamageNumber(data.Position, "+" .. amount, Colors.Heal, false)
			end
			return
		end
		local color = if data.Kill then Colors.HealthLow elseif data.Headshot then Colors.Gold else WHITE
		flashHitMarker(color)
		if data.Position then
			FXController.DamageNumber(data.Position, amount, if data.Headshot then Colors.Gold else WHITE, data.Headshot)
		end
		FXController.PlaySound(Config.Sounds.HitMarker, nil, 0.25, if data.Kill then 0.8 elseif data.Headshot then 1.6 else 1.25)
	end)

	Remotes.Damaged.OnClientEvent:Connect(function(data)
		local amount = if typeof(data) == "table" and typeof(data.Amount) == "number" then data.Amount else 10
		ui.DamageFlash.BackgroundTransparency = math.clamp(0.9 - amount / 150, 0.6, 0.9)
		TweenService:Create(ui.DamageFlash, TweenInfo.new(0.35), { BackgroundTransparency = 1 }):Play()
	end)

	Remotes.FX.OnClientEvent:Connect(function(kind, data)
		if kind ~= "UltActivated" or typeof(data) ~= "table" then
			return
		end
		local ally = data.Team == ClientState.GetMyTeam()
		local hero = Heroes.Get(data.Hero)
		ui.UltFeed.Text = string.format(
			"%s %s%s: %s!",
			if ally then "ALLY" else "ENEMY",
			tostring(data.Name),
			if hero then " (" .. hero.DisplayName .. ")" else "",
			string.upper(tostring(data.Ability))
		)
		ui.UltFeed.TextColor3 = Config.GetTeamColor(data.Team)
		ui.UltFeed.Visible = true
		ultFeedToken += 1
		local token = ultFeedToken
		task.delay(2.5, function()
			if ultFeedToken == token then
				ui.UltFeed.Visible = false
			end
		end)
	end)

	ClientState.AbilityDenied:Connect(function(slot)
		deniedFlash[slot] = os.clock() + 0.25
	end)
end

return HUDController
