package com.focusforceplus.app.study.ai

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class StudyAiModule {
    // 目前只有本地规则实现，直接绑定；接入远程 provider 后应改为绑定
    // ResilientStudyAiProvider(远程实现, OfflineStudyAiProvider)，否则超时与失败回退不会生效。
    @Binds abstract fun studyAiProvider(provider: OfflineStudyAiProvider): StudyAiProvider
}
