--[[
	FX
	==
	Helpers for sending visual effects and messages to clients. The server decides WHAT happens;
	each client draws it (see the client's FXController and HUDController).
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Remotes = require(ReplicatedStorage:WaitForChild("Shared").Remotes)

local FX = {}

-- Visual effect for everyone.
function FX.All(kind: string, data)
	Remotes.FX:FireAllClients(kind, data)
end

-- Visual effect for everyone except `player` (who already drew it locally for instant feedback).
function FX.Except(player: Player?, kind: string, data)
	if not player then
		FX.All(kind, data)
		return
	end
	for _, other in Players:GetPlayers() do
		if other ~= player then
			Remotes.FX:FireClient(other, kind, data)
		end
	end
end

-- Kill feed entries, banners and other messages for everyone.
function FX.Notify(kind: string, data)
	Remotes.Notify:FireAllClients(kind, data)
end

function FX.NotifyPlayer(player: Player?, kind: string, data)
	if player then
		Remotes.Notify:FireClient(player, kind, data)
	end
end

return FX
