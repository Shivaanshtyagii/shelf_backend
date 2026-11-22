package com.shelf.moderation.internal.model;

import java.util.List;

public record ModerationActivity(List<ModerationReport> reports, List<ModerationAppeal> appeals) {}
