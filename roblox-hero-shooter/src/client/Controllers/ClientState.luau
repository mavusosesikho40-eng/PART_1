--[[
	ClientState
	===========
	Small shared state for the client controllers: which menus are open (menus free the mouse),
	and quick access to this player's profile and the match state replicated by the server.
]]

local Players = game:GetService("Players")
local ReplicatedStorage = game:GetService("ReplicatedStorage")

local Shared = ReplicatedStorage:WaitForChild("Shared")
local Signal = require(Shared.Signal)

local player = Players.LocalPlayer

local ClientState = {}

ClientState.MenuChanged = Signal.new()
ClientState.AbilityDenied = Signal.new() -- (slot) pressed an ability that isn't ready

ClientState.Combatants = ReplicatedStorage:WaitForChild("Combatants")
ClientState.MatchState = ReplicatedStorage:WaitForChild("MatchState")

local menus = {}

function ClientState.SetMenu(name: string, open: boolean)
	if (menus[name] == true) == open then
		return
	end
	menus[name] = if open then true else nil
	ClientState.MenuChanged:Fire()
end

-- With a name: is that menu open? Without: is ANY menu open?
function ClientState.IsMenuOpen(name: string?): boolean
	if name then
		return menus[name] == true
	end
	return next(menus) ~= nil
end

-- This player's profile folder (attributes: Team, Hero, UltCharge, Kills, ...).
function ClientState.GetProfile(): Folder?
	return ClientState.Combatants:FindFirstChild("P" .. player.UserId)
end

function ClientState.GetMyTeam(): string?
	local profile = ClientState.GetProfile()
	local team = profile and profile:GetAttribute("Team")
	return if team and team ~= "" then team else nil
end

function ClientState.GetPhase(): string
	return ClientState.MatchState:GetAttribute("Phase") or "Waiting"
end

-- Seconds left in the current phase, or nil if the phase has no timer.
function ClientState.TimeLeft(): number?
	local endsAt = ClientState.MatchState:GetAttribute("PhaseEndsAt") or 0
	if endsAt <= 0 then
		return nil
	end
	return math.max(0, endsAt - workspace:GetServerTimeNow())
end

-- Our character if it is alive, else nil.
function ClientState.GetLivingCharacter(): Model?
	local character = player.Character
	local humanoid = character and character:FindFirstChildOfClass("Humanoid")
	if humanoid and humanoid.Health > 0 then
		return character
	end
	return nil
end

return ClientState
