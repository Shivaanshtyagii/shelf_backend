package com.shelf.api.moderation.domain;

import java.util.List;

public record ModerationActivity(List<ModerationReport> reports, List<ModerationAppeal> appeals) {}
