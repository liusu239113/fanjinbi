extends Node

@export var audio_data: Dictionary[String, AudioStream] = {}
@export var current_playing_audio_amount: Dictionary[String, int] = {}
@export var current_bgm: AudioStreamPlayer

func play_sound_effect(audio_key: String, pitch = 1.0):
    if not audio_data.has(audio_key):
        push_warning("Trying to play an audio file that does not exist: " + audio_key)
        return

    if current_playing_audio_amount.has(audio_key):
        if current_playing_audio_amount[audio_key] > 5: return
        current_playing_audio_amount[audio_key] += 1
    else:
        current_playing_audio_amount[audio_key] = 1
    var audio_stream: = audio_data[audio_key]
    var stream_player: = AudioStreamPlayer.new()
    stream_player.set_bus("Sound")
    add_child(stream_player)
    stream_player.stream = audio_stream
    stream_player.play()

    stream_player.finished.connect(func():
        if current_playing_audio_amount.has(audio_key):
            current_playing_audio_amount[audio_key] -= 1
            if current_playing_audio_amount[audio_key] <= 0:
                current_playing_audio_amount.erase(audio_key)
        stream_player.queue_free()
    , CONNECT_ONE_SHOT)

func play_bgm(audio_key: String, fade_duration = 3.0):
    if not audio_data.has(audio_key):
        push_warning("Trying to play an audio file that does not exist: " + audio_key)
        return
    if current_bgm:
        var previous = current_bgm
        var fade_out_tween = TweenHelper.tween("fade_out_" + str(randi()), self)
        fade_out_tween.tween_property(previous, "volume_linear", 0, fade_duration)
        fade_out_tween.tween_callback(previous.queue_free)
    var stream_player: = AudioStreamPlayer.new()
    var audio_stream: = audio_data[audio_key]
    stream_player.set_bus("Music")
    add_child(stream_player)
    stream_player.stream = audio_stream
    stream_player.play()
    stream_player.volume_linear = 0.0
    TweenHelper.tween("fade_in_" + str(randi()), self).tween_property(stream_player, "volume_linear", 1.0, fade_duration)
    current_bgm = stream_player
