
"use strict";

document.addEventListener("DOMContentLoaded", function () {

    const progress = document.querySelector(
        ".loading-progress"
    );

    const loadingText = document.querySelector(
        ".loading-text"
    );

    if (!progress || !loadingText) {
        return;
    }

    const messages = [
        "INITIALIZING SYSTEM",
        "PREPARING ENVIRONMENT",
        "LOADING COMPONENTS",
        "STARTING RONiLiFEOS"
    ];

    let current = 0;

    const messageTimer = setInterval(function () {

        current = (current + 1) % messages.length;

        loadingText.textContent = messages[current];

    }, 800);

    // This script controls presentation only.
    // Native Java controls actual startup navigation.

});
