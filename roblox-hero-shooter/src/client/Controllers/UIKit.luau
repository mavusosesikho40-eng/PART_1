--[[
	UIKit
	=====
	Tiny helpers for building UI in code, plus automatic scaling: every panel is designed for a
	1280x720 screen and shrinks or grows to fit phones, tablets and large monitors.
]]

local UIKit = {}

UIKit.Colors = {
	Panel = Color3.fromRGB(12, 14, 22),
	PanelLight = Color3.fromRGB(30, 34, 48),
	Stroke = Color3.fromRGB(72, 80, 102),
	Text = Color3.fromRGB(242, 244, 250),
	Muted = Color3.fromRGB(160, 167, 186),
	Health = Color3.fromRGB(232, 238, 246),
	HealthLow = Color3.fromRGB(255, 86, 76),
	Shield = Color3.fromRGB(120, 220, 255),
	Gold = Color3.fromRGB(255, 206, 92),
	Heal = Color3.fromRGB(120, 255, 160),
}

UIKit.Fonts = {
	Black = Enum.Font.GothamBlack,
	Bold = Enum.Font.GothamBold,
	Medium = Enum.Font.GothamMedium,
	Regular = Enum.Font.Gotham,
}

-- Creates an instance, sets properties, then parents it (Parent is applied last).
function UIKit.Create(className: string, props)
	local instance = Instance.new(className)
	for key, value in props or {} do
		if key ~= "Parent" then
			instance[key] = value
		end
	end
	if props and props.Parent then
		instance.Parent = props.Parent
	end
	return instance
end

function UIKit.Frame(props)
	props.BorderSizePixel = 0
	return UIKit.Create("Frame", props)
end

function UIKit.Text(props)
	local merged = {
		BackgroundTransparency = 1,
		BorderSizePixel = 0,
		Font = UIKit.Fonts.Bold,
		TextColor3 = UIKit.Colors.Text,
		TextSize = 16,
		Text = "",
	}
	for key, value in props do
		merged[key] = value
	end
	return UIKit.Create("TextLabel", merged)
end

function UIKit.Button(props)
	local merged = {
		AutoButtonColor = false,
		BorderSizePixel = 0,
		Font = UIKit.Fonts.Black,
		TextColor3 = UIKit.Colors.Text,
		TextSize = 18,
		BackgroundColor3 = UIKit.Colors.PanelLight,
	}
	for key, value in props do
		merged[key] = value
	end
	return UIKit.Create("TextButton", merged)
end

function UIKit.Corner(parent: Instance, radius: number?)
	return UIKit.Create("UICorner", { CornerRadius = UDim.new(0, radius or 8), Parent = parent })
end

function UIKit.Stroke(parent: Instance, color: Color3?, thickness: number?, transparency: number?)
	return UIKit.Create("UIStroke", {
		Color = color or UIKit.Colors.Stroke,
		Thickness = thickness or 1,
		Transparency = transparency or 0,
		ApplyStrokeMode = Enum.ApplyStrokeMode.Border,
		Parent = parent,
	})
end

function UIKit.FormatTime(seconds: number?): string
	local total = math.max(0, math.ceil(seconds or 0))
	return string.format("%d:%02d", math.floor(total / 60), total % 60)
end

--------------------------------------------------------------------------------
-- Auto scaling
--------------------------------------------------------------------------------
local scales = {}
local currentScale = 1

local function computeScale(): number
	local camera = workspace.CurrentCamera
	local size = if camera then camera.ViewportSize else Vector2.new(1280, 720)
	if size.X < 2 or size.Y < 2 then
		return 1
	end
	return math.clamp(math.min(size.X / 1280, size.Y / 720), 0.55, 1.3)
end

local function rescale()
	currentScale = computeScale()
	for _, scale in scales do
		scale.Scale = currentScale
	end
end

-- Adds a UIScale that keeps `guiObject` sized for the current screen (scales around its AnchorPoint).
function UIKit.AutoScale(guiObject: GuiObject)
	local scale = Instance.new("UIScale")
	scale.Scale = currentScale
	scale.Parent = guiObject
	table.insert(scales, scale)
	return scale
end

local cameraConnection = nil
local function watchCamera()
	if cameraConnection then
		cameraConnection:Disconnect()
	end
	local camera = workspace.CurrentCamera
	if camera then
		cameraConnection = camera:GetPropertyChangedSignal("ViewportSize"):Connect(rescale)
	end
	rescale()
end
workspace:GetPropertyChangedSignal("CurrentCamera"):Connect(watchCamera)
watchCamera()

return UIKit
