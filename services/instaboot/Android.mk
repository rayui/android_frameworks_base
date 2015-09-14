#
# Copyright (C) 2007 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# Author : Frank Chen
# Mail : frank.chen@amlogic.com
#/

LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := services.instaboot:lib/instaboot_static.jar

include $(BUILD_MULTI_PREBUILT)



instaboot_bin := $(strip $(wildcard $(LOCAL_PATH)/lib/instabootserver))

ifneq ($(instaboot_bin),)
include $(CLEAR_VARS)
LOCAL_MODULE := instabootserver
LOCAL_SRC_FILES := lib/instabootserver
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT)/bin
include $(BUILD_PREBUILT)
endif

include $(call all-makefiles-under,$(LOCAL_PATH))
