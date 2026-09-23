extends ScrollContainer

func _ready() -> void :
    if SaveContext.save_data.options.has("master_volume"):
        AudioServer.set_bus_volume_linear(AudioServer.get_bus_index("Master"), SaveContext.save_data.options["master_volume"])
    if SaveContext.save_data.options.has("sound_volume"):
        AudioServer.set_bus_volume_linear(AudioServer.get_bus_index("Sound"), SaveContext.save_data.options["sound_volume"])
    if SaveContext.save_data.options.has("music_volume"):
        AudioServer.set_bus_volume_linear(AudioServer.get_bus_index("Music"), SaveContext.save_data.options["music_volume"])

    % MasterVolumeSlider.value = AudioServer.get_bus_volume_linear(AudioServer.get_bus_index("Master"))
    % SoundEffectVolumeSlider.value = AudioServer.get_bus_volume_linear(AudioServer.get_bus_index("Sound"))
    % MusicVolumeSlider.value = AudioServer.get_bus_volume_linear(AudioServer.get_bus_index("Music"))

func _on_master_volume_slider_drag_ended(_value_changed: bool) -> void :
    SaveContext.save_data.options["master_volume"] = % MasterVolumeSlider.value
    AudioServer.set_bus_volume_linear(AudioServer.get_bus_index("Master"), % MasterVolumeSlider.value)

func _on_sound_effect_volume_slider_drag_ended(_value_changed: bool) -> void :
    SaveContext.save_data.options["sound_volume"] = % SoundEffectVolumeSlider.value
    AudioServer.set_bus_volume_linear(AudioServer.get_bus_index("Sound"), % SoundEffectVolumeSlider.value)

func _on_music_volume_slider_drag_ended(_value_changed: bool) -> void :
    SaveContext.save_data.options["music_volume"] = % MusicVolumeSlider.value
    AudioServer.set_bus_volume_linear(AudioServer.get_bus_index("Music"), % MusicVolumeSlider.value)
