--[[
	GameState
	=========
	The current match phase and scoreboard numbers. Values are mirrored onto
	ReplicatedStorage.MatchState as attributes so every client can read them for the HUD.

	Phases: "Waiting" -> "Intermission" -> ("Setup" -> "Battle" -> "RoundEnd") x rounds -> "MatchEnd"
]]

local ReplicatedStorage = game:GetService("ReplicatedStorage")

local GameState = {}

GameState.Phase = "Waiting"
GameState.DamageEnabled = false

local folder = ReplicatedStorage:FindFirstChild("MatchState")
if not folder then
	folder = Instance.new("Folder")
	folder.Name = "MatchState"
	folder.Parent = ReplicatedStorage
end
GameState.Folder = folder

function GameState.Set(key: string, value)
	folder:SetAttribute(key, value)
end

function GameState.Get(key: string)
	return folder:GetAttribute(key)
end

-- `duration` (seconds) lets clients show a countdown; nil means "no timer".
function GameState.SetPhase(phase: string, duration: number?)
	GameState.Phase = phase
	GameState.DamageEnabled = phase == "Battle"
	folder:SetAttribute("Phase", phase)
	folder:SetAttribute("PhaseEndsAt", if duration then workspace:GetServerTimeNow() + duration else 0)
end

GameState.SetPhase("Waiting", nil)

return GameState
