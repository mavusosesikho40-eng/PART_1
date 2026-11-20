--[[
	HeroVisuals
	===========
	Gives each hero a recognisable look by welding simple "gear" parts (shoulder pads, visors,
	weapons, halos...) onto the player's own avatar. Works on R15 and R6 bodies.

	Want real models? Replace a hero's builder with code that clones a Model from ServerStorage
	and welds it on the same way.
]]

local HeroVisuals = {}

local DARK = Color3.fromRGB(35, 38, 48)
local ROTATE_UPRIGHT = CFrame.Angles(0, 0, math.rad(90)) -- turns a cylinder from lying down to standing

local function findPart(character: Model, names): BasePart?
	for _, name in names do
		local part = character:FindFirstChild(name)
		if part and part:IsA("BasePart") then
			return part
		end
	end
	return nil
end

local TORSO = { "UpperTorso", "Torso" }
local HEAD = { "Head" }
local LEFT_ARM = { "LeftUpperArm", "Left Arm" }
local RIGHT_ARM = { "RightUpperArm", "Right Arm" }

-- Welds a part to a body part at `offset` (relative to that body part).
local function gear(folder: Model, body: BasePart?, size: Vector3, offset: CFrame, color: Color3, props)
	if not body then
		return nil
	end
	props = props or {}
	local part = Instance.new("Part")
	part.Name = "Gear"
	part.Size = size
	part.Shape = props.Shape or Enum.PartType.Block
	part.Material = props.Material or Enum.Material.SmoothPlastic
	part.Color = color
	part.Transparency = props.Transparency or 0
	part.CanCollide = false
	part.CanQuery = false -- gear never blocks or absorbs shots
	part.CanTouch = false
	part.Massless = true
	part.CastShadow = false
	part.CFrame = body.CFrame * offset
	local weld = Instance.new("WeldConstraint")
	weld.Part0 = body
	weld.Part1 = part
	weld.Parent = part
	part.Parent = folder
	return part
end

local builders = {}

function builders.Bulwark(character, folder, hero)
	local torso, leftArm, rightArm = findPart(character, TORSO), findPart(character, LEFT_ARM), findPart(character, RIGHT_ARM)
	gear(folder, leftArm, Vector3.new(1.4, 0.55, 1.4), CFrame.new(0, 0.6, 0), hero.Color, { Material = Enum.Material.Metal })
	gear(folder, rightArm, Vector3.new(1.4, 0.55, 1.4), CFrame.new(0, 0.6, 0), hero.Color, { Material = Enum.Material.Metal })
	-- arm shield and cannon
	gear(folder, leftArm, Vector3.new(0.3, 2.4, 2), CFrame.new(-0.75, -0.6, 0), hero.AccentColor, { Material = Enum.Material.Neon, Transparency = 0.35 })
	gear(folder, torso, Vector3.new(0.9, 0.9, 3.2), CFrame.new(1.35, -0.3, -1.3), DARK, { Material = Enum.Material.Metal })
	gear(folder, torso, Vector3.new(0.6, 0.6, 0.3), CFrame.new(1.35, -0.3, -2.95), hero.AccentColor, { Material = Enum.Material.Neon })
end

function builders.Kestrel(character, folder, hero)
	local torso, head = findPart(character, TORSO), findPart(character, HEAD)
	gear(folder, head, Vector3.new(1.05, 0.3, 0.25), CFrame.new(0, 0.15, -0.55), hero.AccentColor, { Material = Enum.Material.Neon })
	gear(folder, torso, Vector3.new(1.3, 1.4, 0.6), CFrame.new(0, 0.1, 0.8), hero.Color, { Material = Enum.Material.Metal })
	gear(folder, torso, Vector3.new(0.35, 0.5, 3.6), CFrame.new(1.2, -0.2, -1.5), DARK, { Material = Enum.Material.Metal })
	gear(folder, torso, Vector3.new(0.4, 0.25, 0.9), CFrame.new(1.2, 0.15, -0.9), hero.AccentColor, { Material = Enum.Material.Neon })
end

function builders.Volta(character, folder, hero)
	local torso, head = findPart(character, TORSO), findPart(character, HEAD)
	gear(folder, torso, Vector3.new(1.1, 1.1, 1.1), CFrame.new(1.7, 0.9, -0.7), hero.AccentColor, { Shape = Enum.PartType.Ball, Material = Enum.Material.Neon })
	gear(folder, head, Vector3.new(0.15, 1.5, 1.5), CFrame.new(0, 0.8, 0) * ROTATE_UPRIGHT, hero.AccentColor, { Shape = Enum.PartType.Cylinder, Material = Enum.Material.Neon, Transparency = 0.3 })
	gear(folder, torso, Vector3.new(2.1, 0.3, 1.2), CFrame.new(0, 0.95, 0), hero.Color, { Material = Enum.Material.Metal })
end

function builders.Kaida(character, folder, hero)
	local torso, head = findPart(character, TORSO), findPart(character, HEAD)
	gear(folder, torso, Vector3.new(0.15, 0.4, 3), CFrame.new(1.2, -0.6, -1.3), hero.AccentColor, { Material = Enum.Material.Neon })
	gear(folder, torso, Vector3.new(0.15, 0.4, 3), CFrame.new(-1.2, -0.6, -1.3), hero.AccentColor, { Material = Enum.Material.Neon })
	gear(folder, head, Vector3.new(1.05, 0.45, 0.2), CFrame.new(0, -0.2, -0.55), DARK)
	gear(folder, torso, Vector3.new(1.4, 0.35, 1.1), CFrame.new(0, 1.0, 0.1), hero.Color, { Material = Enum.Material.Fabric })
end

function builders.Lumen(character, folder, hero)
	local torso, head = findPart(character, TORSO), findPart(character, HEAD)
	gear(folder, head, Vector3.new(0.12, 1.9, 1.9), CFrame.new(0, 1.1, 0) * ROTATE_UPRIGHT, hero.AccentColor, { Shape = Enum.PartType.Cylinder, Material = Enum.Material.Neon, Transparency = 0.25 })
	gear(folder, torso, Vector3.new(5, 0.25, 0.25), CFrame.new(1.35, 0, -0.5) * ROTATE_UPRIGHT, hero.Color, { Shape = Enum.PartType.Cylinder, Material = Enum.Material.Metal })
	gear(folder, torso, Vector3.new(0.8, 0.8, 0.8), CFrame.new(1.35, 2.6, -0.5), hero.AccentColor, { Shape = Enum.PartType.Ball, Material = Enum.Material.Neon })
end

function HeroVisuals.Apply(character: Model, hero, teamColor: Color3)
	local existing = character:FindFirstChild("HeroGear")
	if existing then
		existing:Destroy()
	end
	local folder = Instance.new("Model")
	folder.Name = "HeroGear"
	folder.Parent = character

	local builder = builders[hero.Id]
	if builder then
		builder(character, folder, hero)
	end
	-- A team-coloured chest light on every hero so teams are easy to tell apart.
	gear(folder, findPart(character, TORSO), Vector3.new(0.9, 0.5, 0.15), CFrame.new(0, 0.25, -0.55), teamColor, { Material = Enum.Material.Neon })
end

return HeroVisuals
