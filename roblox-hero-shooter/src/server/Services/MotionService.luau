--[[
	MotionService
	=============
	Moves characters for dashes, leaps and knockback. Players' characters are simulated on their
	own computer, so we ask that client to apply the motion; bots are moved right here.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Remotes = require(Shared.Remotes)
local MotionUtil = require(Shared.MotionUtil)

local MotionService = {}

function MotionService.Dash(character: Model, velocity: Vector3, duration: number)
	local player = Players:GetPlayerFromCharacter(character)
	if player then
		Remotes.Motion:FireClient(player, "Dash", velocity, duration)
	else
		MotionUtil.Dash(character, velocity, duration)
	end
end

function MotionService.Impulse(character: Model, velocity: Vector3)
	local player = Players:GetPlayerFromCharacter(character)
	if player then
		Remotes.Motion:FireClient(player, "Impulse", velocity, 0)
	else
		MotionUtil.Impulse(character, velocity)
	end
end

-- A short, strong shove.
function MotionService.Knockback(character: Model, velocity: Vector3)
	MotionService.Dash(character, velocity, 0.15)
end

-- Teleports work from the server for everyone.
function MotionService.Teleport(character: Model, cframe: CFrame)
	character:PivotTo(cframe)
end

return MotionService
