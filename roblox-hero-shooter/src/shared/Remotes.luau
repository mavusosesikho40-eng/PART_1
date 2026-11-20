--[[
	Remotes
	=======
	All network events in one place. The server creates them; clients wait for them.

	  Client -> Server
	    SelectHero  (heroId)                 pick or swap hero
	    Fire        (shotData)               primary weapon
	    Reload      ()
	    UseAbility  (slot, aim)              "Ability1" | "Ability2" | "Ultimate"

	  Server -> Client
	    FX          (kind, data)             visual effects (tracers, explosions, ...)
	    HitConfirm  (data)                   your damage/healing landed: hit markers, numbers
	    Damaged     (data)                   you took damage: screen flash
	    Notify      (kind, data)             kill feed, banners, messages
	    Motion      (kind, velocity, time)   dash/knockback YOUR character (clients own their physics)
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")
local RunService = game:GetService("RunService")

local EVENT_NAMES = {
	"SelectHero",
	"Fire",
	"Reload",
	"UseAbility",
	"FX",
	"HitConfirm",
	"Damaged",
	"Notify",
	"Motion",
}

local Remotes = {}

local folder
if RunService:IsServer() then
	folder = ReplicatedStorage:FindFirstChild("Remotes")
	if not folder then
		folder = Instance.new("Folder")
		folder.Name = "Remotes"
		folder.Parent = ReplicatedStorage
	end
	for _, name in EVENT_NAMES do
		if not folder:FindFirstChild(name) then
			local remote = Instance.new("RemoteEvent")
			remote.Name = name
			remote.Parent = folder
		end
	end
else
	folder = ReplicatedStorage:WaitForChild("Remotes")
end

for _, name in EVENT_NAMES do
	Remotes[name] = folder:WaitForChild(name)
end

return Remotes
