--[[
	FXController
	============
	Draws every visual effect on this client: tracers, projectiles, explosions, rings, lightning,
	debris and floating damage numbers. Effects are local parts in Workspace.ClientFX that never
	collide or block shots. The server tells us what to draw through the "FX" remote; our own
	shots are drawn instantly by CombatController so they feel responsive.
]]

local Debris = game:GetService("Debris")
local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")
local SoundService = game:GetService("SoundService")
local TweenService = game:GetService("TweenService")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)
local Remotes = require(Shared.Remotes)

local FXController = {}

local UPRIGHT = CFrame.Angles(0, 0, math.rad(90))

local folder: Folder? = nil
local projectiles = {} -- [id] = { Part, Position, Velocity, Gravity, Age, Lifetime }
local followers = {} -- [part] = { Root, Expires, Offset } effects that follow a character
local rng = Random.new()

local function getFolder(): Folder
	if not folder or not folder.Parent then
		folder = Instance.new("Folder")
		folder.Name = "ClientFX"
		folder.Parent = workspace
	end
	return folder :: Folder
end

local function effectPart(props): Part
	local part = Instance.new("Part")
	part.Anchored = true
	part.CanCollide = false
	part.CanQuery = false
	part.CanTouch = false
	part.CastShadow = false
	part.Material = props.Material or Enum.Material.Neon
	part.Color = props.Color or Color3.new(1, 1, 1)
	part.Transparency = props.Transparency or 0
	if props.Shape then
		part.Shape = props.Shape
	end
	part.Size = props.Size or Vector3.one
	part.CFrame = props.CFrame or CFrame.new()
	part.Parent = getFolder()
	return part
end

local function fadeOut(instance: Instance, duration: number, goals)
	goals = goals or {}
	goals.Transparency = 1
	TweenService:Create(instance, TweenInfo.new(duration, Enum.EasingStyle.Quad, Enum.EasingDirection.Out), goals):Play()
	Debris:AddItem(instance, duration + 0.05)
end

--------------------------------------------------------------------------------
-- Sound
--------------------------------------------------------------------------------

-- Plays a sound in the world at `position`, or as a flat UI sound if position is nil.
function FXController.PlaySound(soundId: string?, position: Vector3?, volume: number?, speed: number?)
	if not soundId or soundId == "" then
		return
	end
	local sound = Instance.new("Sound")
	sound.SoundId = soundId
	sound.Volume = volume or 0.5
	sound.PlaybackSpeed = speed or 1
	if position then
		local attachment = Instance.new("Attachment")
		attachment.Position = position
		attachment.Parent = workspace.Terrain
		sound.RollOffMaxDistance = 250
		sound.Parent = attachment
		sound:Play()
		Debris:AddItem(attachment, 4)
	else
		sound.Parent = SoundService
		sound:Play()
		Debris:AddItem(sound, 4)
	end
end

--------------------------------------------------------------------------------
-- Effects
--------------------------------------------------------------------------------

function FXController.Tracer(origin: Vector3, finish: Vector3, color: Color3, width: number?, duration: number?)
	local length = (finish - origin).Magnitude
	if length < 0.1 then
		return
	end
	local thickness = width or 0.1
	local beam = effectPart({
		Size = Vector3.new(thickness, thickness, length),
		CFrame = CFrame.lookAt(origin, finish) * CFrame.new(0, 0, -length / 2),
		Color = color,
		Transparency = 0.1,
	})
	fadeOut(beam, duration or 0.12)
end

function FXController.MuzzleFlash(position: Vector3, color: Color3)
	local flash = effectPart({ Shape = Enum.PartType.Ball, Size = Vector3.one * 0.7, CFrame = CFrame.new(position), Color = color })
	local light = Instance.new("PointLight")
	light.Color = color
	light.Range = 10
	light.Brightness = 3
	light.Parent = flash
	fadeOut(flash, 0.08)
end

function FXController.Tracers(origin: Vector3, ends, color: Color3, width: number?)
	for _, finish in ends do
		FXController.Tracer(origin, finish, color, width)
	end
	FXController.MuzzleFlash(origin, color)
	FXController.PlaySound(Config.Sounds.Hitscan, origin, 0.35, rng:NextNumber(0.95, 1.1))
end

function FXController.Explosion(position: Vector3, radius: number, color: Color3)
	local ball = effectPart({ Shape = Enum.PartType.Ball, Size = Vector3.one * 2, CFrame = CFrame.new(position), Color = color, Transparency = 0.15 })
	local light = Instance.new("PointLight")
	light.Color = color
	light.Range = radius * 2
	light.Brightness = 4
	light.Parent = ball
	fadeOut(ball, 0.35, { Size = Vector3.one * radius * 2 })
	FXController.PlaySound(Config.Sounds.Explosion, position, 0.6, rng:NextNumber(0.9, 1.1))
end

-- An expanding flat ring on the ground (heals, shockwaves, area abilities).
function FXController.Ring(position: Vector3, radius: number, color: Color3, duration: number?)
	local ring = effectPart({
		Shape = Enum.PartType.Cylinder,
		Size = Vector3.new(0.3, 2, 2),
		CFrame = CFrame.new(position) * UPRIGHT,
		Color = color,
		Transparency = 0.25,
	})
	fadeOut(ring, duration or 0.45, { Size = Vector3.new(0.3, radius * 2, radius * 2) })
end

function FXController.Slash(origin: Vector3, direction: Vector3, range: number, color: Color3)
	local blade = effectPart({
		Size = Vector3.new(range * 1.3, 0.2, 0.8),
		CFrame = CFrame.lookAt(origin, origin + direction) * CFrame.new(0, 0, -range * 0.5),
		Color = color,
		Transparency = 0.15,
	})
	fadeOut(blade, 0.16, { Size = Vector3.new(range * 1.6, 0.05, 0.3) })
	FXController.PlaySound(Config.Sounds.Melee, origin, 0.5, rng:NextNumber(0.95, 1.15))
end

function FXController.Blink(from: Vector3, to: Vector3, color: Color3)
	FXController.Tracer(from, to, color, 1.2, 0.3)
	FXController.Ring(from, 4, color, 0.3)
	FXController.Ring(to, 4, color, 0.3)
	FXController.PlaySound(Config.Sounds.Ability, to, 0.6, 1.3)
end

function FXController.Lightning(position: Vector3, radius: number, color: Color3)
	local top = position + Vector3.new(rng:NextNumber(-4, 4), 90, rng:NextNumber(-4, 4))
	local points = { top }
	local segments = 7
	for index = 1, segments - 1 do
		local point = top:Lerp(position, index / segments)
		table.insert(points, point + Vector3.new(rng:NextNumber(-3, 3), 0, rng:NextNumber(-3, 3)))
	end
	table.insert(points, position)
	for index = 1, #points - 1 do
		FXController.Tracer(points[index], points[index + 1], color, 0.7, 0.3)
	end
	FXController.Explosion(position + Vector3.new(0, 1, 0), radius * 0.6, color)
	FXController.Ring(position, radius, color, 0.4)
end

-- A pulsing warning circle that shows where something is about to happen.
function FXController.Telegraph(position: Vector3, radius: number, duration: number, color: Color3)
	local disc = effectPart({
		Shape = Enum.PartType.Cylinder,
		Size = Vector3.new(0.2, radius * 2, radius * 2),
		CFrame = CFrame.new(position + Vector3.new(0, 0.3, 0)) * UPRIGHT,
		Color = color,
		Transparency = 0.8,
	})
	TweenService:Create(disc, TweenInfo.new(0.35, Enum.EasingStyle.Sine, Enum.EasingDirection.InOut, -1, true), {
		Transparency = 0.45,
	}):Play()
	Debris:AddItem(disc, duration)
end

-- Breaks a destroyed piece of cover into tumbling chunks.
function FXController.Shatter(cframe: CFrame, size: Vector3, color: Color3, material: Enum.Material?)
	local count = math.clamp(math.floor(size.X * size.Y * size.Z / 12), 4, 14)
	for _ = 1, count do
		local piece = Instance.new("Part")
		local scale = rng:NextNumber(0.5, 1.4)
		piece.Size = Vector3.new(scale, scale * rng:NextNumber(0.5, 1), scale)
		piece.CFrame = cframe
			* CFrame.new(
				rng:NextNumber(-0.5, 0.5) * size.X,
				rng:NextNumber(-0.5, 0.5) * size.Y,
				rng:NextNumber(-0.5, 0.5) * size.Z
			)
			* CFrame.Angles(rng:NextNumber(0, math.pi * 2), rng:NextNumber(0, math.pi * 2), 0)
		piece.Color = color
		piece.Material = material or Enum.Material.SmoothPlastic
		piece.CanQuery = false
		piece.CanTouch = false
		piece.CollisionGroup = "Debris"
		piece.Parent = getFolder()
		piece.AssemblyLinearVelocity = Vector3.new(rng:NextNumber(-25, 25), rng:NextNumber(10, 35), rng:NextNumber(-25, 25))
		Debris:AddItem(piece, rng:NextNumber(1.8, 2.8))
	end
	FXController.PlaySound(Config.Sounds.Explosion, cframe.Position, 0.35, 1.4)
end

function FXController.DashTrail(character: Model, duration: number, color: Color3)
	local root = character and character:FindFirstChild("HumanoidRootPart")
	if not root then
		return
	end
	local top = Instance.new("Attachment")
	top.Position = Vector3.new(0, 1.2, 0)
	top.Parent = root
	local bottom = Instance.new("Attachment")
	bottom.Position = Vector3.new(0, -1.8, 0)
	bottom.Parent = root
	local trail = Instance.new("Trail")
	trail.Attachment0 = top
	trail.Attachment1 = bottom
	trail.Color = ColorSequence.new(color)
	trail.Transparency = NumberSequence.new(0.3, 1)
	trail.Lifetime = 0.3
	trail.LightEmission = 1
	trail.Parent = root
	for _, item in { trail, top, bottom } do
		Debris:AddItem(item, duration + 0.35)
	end
	FXController.PlaySound(Config.Sounds.Dash, root.Position, 0.5)
end

-- A glowing ring that follows a character for a while (buffs, ultimates).
function FXController.Aura(character: Model, duration: number, color: Color3, radius: number?)
	local root = character and character:FindFirstChild("HumanoidRootPart")
	if not root then
		return
	end
	local size = (radius or 3.5) * 2
	local ring = effectPart({ Shape = Enum.PartType.Cylinder, Size = Vector3.new(0.2, size, size), Color = color, Transparency = 0.55 })
	followers[ring] = { Root = root, Expires = os.clock() + duration, Offset = -2.8 }
end

function FXController.DamageNumber(position: Vector3, text: string, color: Color3, big: boolean?)
	local offset = Vector3.new(rng:NextNumber(-1, 1), rng:NextNumber(0.5, 1.5), rng:NextNumber(-1, 1))
	local anchor = effectPart({ Transparency = 1, Size = Vector3.one * 0.2, CFrame = CFrame.new(position + offset) })
	local billboard = Instance.new("BillboardGui")
	billboard.Size = UDim2.fromOffset(if big then 96 else 72, if big then 36 else 28)
	billboard.AlwaysOnTop = true
	billboard.LightInfluence = 0
	billboard.MaxDistance = 250
	billboard.Parent = anchor
	local label = Instance.new("TextLabel")
	label.BackgroundTransparency = 1
	label.Size = UDim2.fromScale(1, 1)
	label.Font = Enum.Font.GothamBlack
	label.TextScaled = true
	label.Text = text
	label.TextColor3 = color
	label.TextStrokeTransparency = 0.3
	label.Parent = billboard
	TweenService:Create(billboard, TweenInfo.new(0.7, Enum.EasingStyle.Quad, Enum.EasingDirection.Out), {
		StudsOffset = Vector3.new(0, 2.5, 0),
	}):Play()
	TweenService:Create(label, TweenInfo.new(0.7, Enum.EasingStyle.Quad, Enum.EasingDirection.In), {
		TextTransparency = 1,
		TextStrokeTransparency = 1,
	}):Play()
	Debris:AddItem(anchor, 0.75)
end

--------------------------------------------------------------------------------
-- Projectiles (simulated exactly like the server simulates them)
--------------------------------------------------------------------------------

function FXController.SpawnProjectile(data)
	if projectiles[data.Id] then
		return
	end
	local size = math.max(0.6, (data.Radius or 0.5) * 2)
	local color = data.Color or Color3.new(1, 1, 1)
	local orb = effectPart({ Shape = Enum.PartType.Ball, Size = Vector3.one * size, CFrame = CFrame.new(data.Origin), Color = color })

	local top = Instance.new("Attachment")
	top.Position = Vector3.new(0, size * 0.35, 0)
	top.Parent = orb
	local bottom = Instance.new("Attachment")
	bottom.Position = Vector3.new(0, -size * 0.35, 0)
	bottom.Parent = orb
	local trail = Instance.new("Trail")
	trail.Attachment0 = top
	trail.Attachment1 = bottom
	trail.Color = ColorSequence.new(color)
	trail.Transparency = NumberSequence.new(0.3, 1)
	trail.Lifetime = 0.18
	trail.LightEmission = 1
	trail.FaceCamera = true
	trail.Parent = orb
	local light = Instance.new("PointLight")
	light.Color = color
	light.Range = 8
	light.Brightness = 2
	light.Parent = orb

	projectiles[data.Id] = {
		Part = orb,
		Position = data.Origin,
		Velocity = data.Velocity,
		Gravity = data.Gravity or 0,
		Age = 0,
		Lifetime = (data.Lifetime or 3) + 0.3,
	}
	FXController.PlaySound(Config.Sounds.Projectile, data.Origin, 0.35, rng:NextNumber(1.1, 1.3))
end

function FXController.EndProjectile(data)
	local projectile = projectiles[data.Id]
	if projectile then
		projectile.Part:Destroy()
		projectiles[data.Id] = nil
	end
	local color = data.Color or Color3.new(1, 1, 1)
	if data.Impact == "Explosion" then
		FXController.Explosion(data.Position, data.Radius or 6, color)
	else
		FXController.Ring(data.Position, data.Radius or 2, color, 0.2)
	end
end

local function step(dt: number)
	for id, projectile in projectiles do
		projectile.Age += dt
		if projectile.Age > projectile.Lifetime or not projectile.Part.Parent then
			projectile.Part:Destroy()
			projectiles[id] = nil
		else
			local gravity = Vector3.new(0, -projectile.Gravity, 0)
			projectile.Position += projectile.Velocity * dt + gravity * (0.5 * dt * dt)
			projectile.Velocity += gravity * dt
			projectile.Part.CFrame = CFrame.new(projectile.Position)
		end
	end

	local now = os.clock()
	for part, info in followers do
		if now >= info.Expires or not info.Root.Parent then
			followers[part] = nil
			fadeOut(part, 0.2)
		else
			part.CFrame = CFrame.new(info.Root.Position + Vector3.new(0, info.Offset, 0)) * UPRIGHT
		end
	end
end

--------------------------------------------------------------------------------
-- Server events
--------------------------------------------------------------------------------

local handlers = {
	Tracer = function(data)
		FXController.Tracers(data.Origin, data.Ends or {}, data.Color, data.Width)
	end,
	Projectile = FXController.SpawnProjectile,
	ProjectileEnd = FXController.EndProjectile,
	Explosion = function(data)
		FXController.Explosion(data.Position, data.Radius, data.Color)
	end,
	Ring = function(data)
		FXController.Ring(data.Position, data.Radius, data.Color, data.Time)
		FXController.PlaySound(Config.Sounds.Ability, data.Position, 0.4)
	end,
	Burst = function(data)
		FXController.Ring(data.Position, 6, data.Color, 0.3)
		FXController.PlaySound(Config.Sounds.Dash, data.Position, 0.5, 0.8)
	end,
	Slash = function(data)
		FXController.Slash(data.Origin, data.Direction, data.Range, data.Color)
	end,
	Blink = function(data)
		FXController.Blink(data.From, data.To, data.Color)
	end,
	Lightning = function(data)
		FXController.Lightning(data.Position, data.Radius, data.Color)
	end,
	Telegraph = function(data)
		FXController.Telegraph(data.Position, data.Radius, data.Duration, data.Color)
	end,
	Shatter = function(data)
		FXController.Shatter(data.CFrame, data.Size, data.Color, data.Material)
	end,
	DashTrail = function(data)
		FXController.DashTrail(data.Character, data.Duration, data.Color)
	end,
	Aura = function(data)
		FXController.Aura(data.Character, data.Duration, data.Color, data.Radius)
	end,
	UltActivated = function()
		FXController.PlaySound(Config.Sounds.Ultimate, nil, 0.6)
	end,
}

function FXController.Init()
	getFolder()
	RunService.RenderStepped:Connect(step)
	Remotes.FX.OnClientEvent:Connect(function(kind, data)
		local handler = handlers[kind]
		if handler and typeof(data) == "table" then
			local ok, err = pcall(handler, data)
			if not ok then
				warn("[FX] " .. tostring(kind) .. ": " .. tostring(err))
			end
		end
	end)
end

return FXController
