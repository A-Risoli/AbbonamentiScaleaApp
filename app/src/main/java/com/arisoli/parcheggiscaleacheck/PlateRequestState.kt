package com.arisoli.parcheggiscaleacheck

enum class PlateRequestState {
    Idle,
    Sending,
    WaitingResponse,
    Success,
    Error
}
