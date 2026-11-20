--[[
	CameraController
	================
	Over-the-shoulder third-person camera, like most hero shooters:
	  * the mouse is locked to the centre and turns the camera (gamepad: right stick, touch: drag)
	  * your hero always faces where the camera looks, so the crosshair is where you shoot
	  * the camera slides in front of walls instead of clipping through them
	  * with no character (between matches) it slowly circles the arena
	Opening a menu (hero select, scoreboard) frees the mouse.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local UserInputService = game:GetService("UserInputService")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)

local ClientState = require(script.Parent.ClientState)

local player = Players.LocalPlayer
local UserGameSettings = UserSettings():GetService("UserGameSettings")

local CameraController = {}

local settings = Config.Camera
local yaw, pitch = 0, math.rad(-8)
local recoil = 0
local distance = settings.Distance
local orbitAngle = 0
local lastFocus = Vector3.new(0, 20, 0)
local gamepadLook = Vector2.zero
local lookTouches = {} -- touches that started on the right side of the screen steer the camera

local function mapCenter(): Vector3
	local map = workspace:FindFirstChild("Map")
	local point = map and map:FindFirstChild("ControlPoint")
	return if point and point:IsA("BasePart") then point.Position else Vector3.zero
end

-- Kicks the view up a little (weapon recoil). It settles back by itself.
function CameraController.Kick(amount: number)
	recoil += amount
end

local function onCharacterAdded(character: Model)
	recoil = 0
	task.spawn(function()
		-- Face the way the server spawned us (it sets SpawnYaw right after spawning).
		local waited = 0
		while character.Parent and character:GetAttribute("SpawnYaw") == nil and waited < 3 do
			waited += task.wait(0.1)
		end
		local spawnYaw = character:GetAttribute("SpawnYaw")
		if spawnYaw then
			yaw = spawnYaw
			pitch = math.rad(-5)
		end
	end)
end

local function update(dt: number)
	local camera = workspace.CurrentCamera
	if not camera then
		return
	end
	camera.CameraType = Enum.CameraType.Scriptable
	camera.FieldOfView = settings.FieldOfView

	if ClientState.IsMenuOpen() then
		UserInputService.MouseBehavior = Enum.MouseBehavior.Default
		UserInputService.MouseIconEnabled = true
	else
		UserInputService.MouseBehavior = Enum.MouseBehavior.LockCenter
		UserInputService.MouseIconEnabled = false
		local delta = UserInputService:GetMouseDelta()
		local sensitivity = settings.MouseSensitivity * UserGameSettings.MouseSensitivity
		yaw -= delta.X * sensitivity
		pitch -= delta.Y * sensitivity
		if gamepadLook.Magnitude > 0.15 then
			yaw -= gamepadLook.X * settings.GamepadSensitivity * dt
			pitch += gamepadLook.Y * settings.GamepadSensitivity * dt * 0.7
		end
	end
	pitch = math.clamp(pitch, math.rad(settings.MinPitch), math.rad(settings.MaxPitch))
	recoil *= math.exp(-dt * 12)

	local character = player.Character
	local root = character and character:FindFirstChild("HumanoidRootPart")
	local humanoid = character and character:FindFirstChildOfClass("Humanoid")

	if not root then
		-- Nothing to follow: a slow cinematic orbit around the arena.
		orbitAngle += dt * 0.08
		local center = mapCenter()
		local position = center + Vector3.new(math.cos(orbitAngle) * 120, 55, math.sin(orbitAngle) * 120)
		camera.CFrame = CFrame.lookAt(position, center)
		return
	end

	local alive = humanoid ~= nil and humanoid.Health > 0
	local focus = root.Position + Vector3.new(0, settings.Height, 0)
	if alive then
		lastFocus = focus
	else
		focus = lastFocus
	end

	local wantedDistance = if alive then settings.Distance else settings.Distance + 5
	distance += (wantedDistance - distance) * math.min(1, dt * 6)

	local rotation = CFrame.fromOrientation(pitch + recoil, yaw, 0)
	local offset = rotation:VectorToWorldSpace(Vector3.new(settings.ShoulderOffset, 0, distance))

	-- Keep the camera out of walls (but ignore ability barriers and effects).
	local params = RaycastParams.new()
	params.FilterType = Enum.RaycastFilterType.Exclude
	params.FilterDescendantsInstances = { character, workspace:FindFirstChild("Effects") }
	local hit = workspace:Spherecast(focus, 0.5, offset, params)
	local cameraPosition = if hit then focus + offset.Unit * math.max(0.6, hit.Distance - 0.1) else focus + offset

	camera.CFrame = CFrame.new(cameraPosition) * rotation
	camera.Focus = CFrame.new(focus)

	-- Turn the hero to face where we aim.
	if alive and root:IsA("BasePart") then
		humanoid.AutoRotate = false
		root.CFrame = CFrame.new(root.Position) * CFrame.fromOrientation(0, yaw, 0)
	end
end

function CameraController.Init()
	RunService:BindToRenderStep("HeroCamera", Enum.RenderPriority.Camera.Value + 1, update)

	player.CharacterAdded:Connect(onCharacterAdded)
	if player.Character then
		onCharacterAdded(player.Character)
	end

	UserInputService.InputBegan:Connect(function(input, processed)
		if input.UserInputType == Enum.UserInputType.Touch and not processed then
			local camera = workspace.CurrentCamera
			-- The left part of the screen belongs to the movement thumbstick.
			if camera and input.Position.X > camera.ViewportSize.X * 0.35 then
				lookTouches[input] = true
			end
		end
	end)
	UserInputService.InputChanged:Connect(function(input)
		if input.UserInputType == Enum.UserInputType.Touch and lookTouches[input] then
			if not ClientState.IsMenuOpen() then
				yaw -= input.Delta.X * settings.TouchSensitivity
				pitch -= input.Delta.Y * settings.TouchSensitivity
			end
		elseif input.KeyCode == Enum.KeyCode.Thumbstick2 then
			gamepadLook = Vector2.new(input.Position.X, input.Position.Y)
		end
	end)
	UserInputService.InputEnded:Connect(function(input)
		lookTouches[input] = nil
		if input.KeyCode == Enum.KeyCode.Thumbstick2 then
			gamepadLook = Vector2.zero
		end
	end)
end

return CameraController
