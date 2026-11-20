--[[
	MotionUtil
	==========
	Moves a character for dashes, leaps and knockback.

	Roblox lets each player's own computer simulate their character's physics, so a velocity set by
	the server would simply be overwritten. That is why the server asks the owning client to run
	these (see MotionService and CombatController). Bots are simulated by the server, so the server
	calls these directly for them.
]]

local Debris = game:GetService("Debris")

local MotionUtil = {}

local function getAttachment(root: BasePart): Attachment
	local attachment = root:FindFirstChild("MotionAttachment")
	if not attachment then
		attachment = Instance.new("Attachment")
		attachment.Name = "MotionAttachment"
		attachment.Parent = root
	end
	return attachment
end

-- Drives the character at `velocity` for `duration` seconds, ignoring normal movement.
function MotionUtil.Dash(character: Model, velocity: Vector3, duration: number)
	local root = character:FindFirstChild("HumanoidRootPart")
	if not root or not root:IsA("BasePart") then
		return
	end
	local existing = root:FindFirstChild("DashVelocity")
	if existing then
		existing:Destroy()
	end

	local mover = Instance.new("LinearVelocity")
	mover.Name = "DashVelocity"
	mover.Attachment0 = getAttachment(root)
	mover.RelativeTo = Enum.ActuatorRelativeTo.World
	mover.VelocityConstraintMode = Enum.VelocityConstraintMode.Vector
	mover.MaxForce = math.huge
	mover.VectorVelocity = velocity
	mover.Parent = root
	Debris:AddItem(mover, duration)
end

-- Sets the velocity once and lets physics take over (good for small upward pops).
function MotionUtil.Impulse(character: Model, velocity: Vector3)
	local root = character:FindFirstChild("HumanoidRootPart")
	if root and root:IsA("BasePart") then
		root.AssemblyLinearVelocity = velocity
	end
end

return MotionUtil
