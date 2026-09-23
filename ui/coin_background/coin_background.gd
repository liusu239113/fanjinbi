@tool
extends Control

enum COIN_MODE{
    SMALL, 
    MEDIUM, 
    LARGE
}

@export var small_coin_texture: Texture2D
@export var medium_coin_texture: Texture2D
@export var large_coin_texture: Texture2D

@export var current_mode: COIN_MODE = COIN_MODE.SMALL:
    set(value):
        if not is_node_ready():
            await ready
        current_mode = value
        match (current_mode):
            COIN_MODE.SMALL:
                % Sprite.texture = small_coin_texture
            COIN_MODE.MEDIUM:
                % Sprite.texture = medium_coin_texture
            COIN_MODE.LARGE:
                % Sprite.texture = large_coin_texture
        % Parallax2D.repeat_size = ( % Sprite.texture as Texture2D).get_size()
