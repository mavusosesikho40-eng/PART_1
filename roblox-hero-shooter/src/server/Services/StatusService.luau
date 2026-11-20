--[[
	StatusService
	=============
	Temporary buffs and debuffs. Each effect has a name (shown on the HUD), a duration, and one
	or more modifiers. Applying the same name again refreshes it instead of stacking.

		StatusService.Apply(character, "Tailwind", { Speed = 1.3 }, 4)
		StatusService.Apply(character, "Slowed", { Speed = 0.6 }, 2.5)
		StatusService.Apply(character, "Overdrive", { FireRate = 1.5, DamageDealt = 1.25, InfiniteAmmo = 1 }, 8)

	Modifiers (multiply together when several effects are active):
	  Speed, DamageDealt, DamageTaken, FireRate      multipliers (1 = normal)
	  InfiniteAmmo                                   flag (any value)

	Shields are temporary bonus health that absorbs damage before health does.
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Config = require(Shared.Config)

local StatusService = {}

local records = {} -- [character] = { Effects = { [name] = { Mods, Expires } }, BaseSpeed, ShieldExpires }

local function multiplierFor(record, modType: string): number
	local value = 1
	for _, effect in record.Effects do
		local modifier = effect.Mods[modType]
		if modifier then
			value *= modifier
		end
	end
	return value
end

local function hasFlag(record, modType: string): boolean
	for _, effect in record.Effects do
		if effect.Mods[modType] then
			return true
		end
	end
	return false
end

-- Pushes the combined effect values onto the character (WalkSpeed + attributes for the HUD).
local function refresh(character: Model, record)
	local humanoid = character:FindFirstChildOfClass("Humanoid")
	if humanoid and humanoid.Health > 0 then
		humanoid.WalkSpeed = record.BaseSpeed * multiplierFor(record, "Speed")
	end
	character:SetAttribute("FireRateMult", multiplierFor(record, "FireRate"))
	character:SetAttribute("InfiniteAmmo", hasFlag(record, "InfiniteAmmo"))

	local names = {}
	for name in record.Effects do
		table.insert(names, name)
	end
	table.sort(names)
	character:SetAttribute("Effects", table.concat(names, ","))
end

function StatusService.Setup(character: Model, baseWalkSpeed: number)
	local record = { Effects = {}, BaseSpeed = baseWalkSpeed, ShieldExpires = nil }
	records[character] = record
	character:SetAttribute("Shield", 0)
	refresh(character, record)
	character.AncestryChanged:Connect(function()
		if not character:IsDescendantOf(game) then
			records[character] = nil
		end
	end)
end

function StatusService.Clear(character: Model)
	records[character] = nil
end

function StatusService.Apply(character: Model, name: string, mods, duration: number)
	local record = records[character]
	if not record then
		return
	end
	record.Effects[name] = { Mods = mods, Expires = os.clock() + duration }
	refresh(character, record)
end

function StatusService.Remove(character: Model, name: string)
	local record = records[character]
	if record and record.Effects[name] then
		record.Effects[name] = nil
		refresh(character, record)
	end
end

function StatusService.GetMultiplier(character: Model?, modType: string): number
	local record = character and records[character]
	return if record then multiplierFor(record, modType) else 1
end

function StatusService.Has(character: Model?, modType: string): boolean
	local record = character and records[character]
	return if record then hasFlag(record, modType) else false
end

-- Adds temporary shields (they all vanish when the longest one runs out).
function StatusService.AddShield(character: Model, amount: number, duration: number)
	local record = records[character]
	if not record then
		return
	end
	local current = character:GetAttribute("Shield") or 0
	character:SetAttribute("Shield", math.min(Config.Combat.MaxShield, current + amount))
	record.ShieldExpires = math.max(record.ShieldExpires or 0, os.clock() + duration)
end

RunService.Heartbeat:Connect(function()
	local now = os.clock()
	for character, record in records do
		local changed = false
		for name, effect in record.Effects do
			if now >= effect.Expires then
				record.Effects[name] = nil
				changed = true
			end
		end
		if changed then
			refresh(character, record)
		end
		if record.ShieldExpires and now >= record.ShieldExpires then
			record.ShieldExpires = nil
			character:SetAttribute("Shield", 0)
		end
	end
end)

return StatusService
