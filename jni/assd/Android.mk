LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PRELINK_MODULE := false
LOCAL_MODULE      := libassd
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES   := ASSDTerminal.cpp
LOCAL_SHARED_LIBRARIES += liblog
include $(BUILD_SHARED_LIBRARY)

