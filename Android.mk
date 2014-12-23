LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_SRC_FILES += \
      src/org/simalliance/openmobileapi/terminal/service/ITerminalService.aidl

LOCAL_PACKAGE_NAME := AssdTerminal
LOCAL_CERTIFICATE := platform

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))
