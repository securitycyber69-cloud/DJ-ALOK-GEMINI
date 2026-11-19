package com.example.domain.router

import com.example.domain.model.AppSettings
import com.example.domain.model.RoutingDecision
import com.example.domain.model.TaskCategory

interface ModelRouter {
  fun classifyTask(prompt: String, hasImages: Boolean): TaskCategory

  fun route(
    prompt: String,
    hasImages: Boolean,
    imageCount: Int,
    requestedModelId: String?,
    settings: AppSettings,
  ): Result<RoutingDecision>
}
