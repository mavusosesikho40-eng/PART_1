--[[
	Signal
	======
	A tiny event object for code-to-code communication (like a BindableEvent, but plain Luau).

		local died = Signal.new()
		local connection = died:Connect(function(who) print(who, "died") end)
		died:Fire("Echo")
		connection:Disconnect()
]]

local Signal = {}
Signal.__index = Signal

function Signal.new()
	return setmetatable({ _listeners = {} }, Signal)
end

function Signal:Connect(callback)
	local listeners = self._listeners
	local entry = { Callback = callback, Connected = true }
	table.insert(listeners, entry)

	return {
		Disconnect = function()
			entry.Connected = false
			local index = table.find(listeners, entry)
			if index then
				table.remove(listeners, index)
			end
		end,
	}
end

function Signal:Fire(...)
	-- Copy first so listeners can disconnect themselves while we loop.
	for _, entry in table.clone(self._listeners) do
		if entry.Connected then
			task.spawn(entry.Callback, ...)
		end
	end
end

return Signal
