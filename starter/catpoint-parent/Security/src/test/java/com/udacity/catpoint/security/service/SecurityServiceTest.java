package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.FakeImageService;
import com.udacity.catpoint.security.data.*;
import junit.framework.TestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.rekognition.endpoints.internal.Value;

import java.awt.image.BufferedImage;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;


@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest extends TestCase {
    private SecurityService securityService;
    @Mock
    SecurityRepository securityRepository;
    @Mock
    FakeImageService imageService;
    Sensor sensor_door = new Sensor("Door", SensorType.DOOR);
    Sensor sensor_window = new Sensor("Window", SensorType.WINDOW);
    Sensor sensor_motion = new Sensor("Motion", SensorType.MOTION);

    BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
    @BeforeEach
    void init() {
        securityService = new SecurityService(securityRepository, imageService);
    }


    //#1. Alarm is armed and a sensor becomes activated, put the system into pending alarm status.
    @Test
    @DisplayName("Test #1.1")
    public void alarmArmedAwayAndSensorActivated_changeStatusPutToPending() {
        doReturn(ArmingStatus.ARMED_AWAY).when(securityRepository).getArmingStatus();
        doReturn(AlarmStatus.NO_ALARM).when(securityRepository).getAlarmStatus();
        securityService.changeSensorActivationStatus(sensor_window, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    @Test
    @DisplayName("Test #1.2")
    public void alarmArmedHomeAndSensorActivated_changeStatusPutToPending() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor_window, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    //#2. Alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.
    @Test
    @DisplayName("Test #2.1 - ArmingStatus: ARMED_AWAY")
    public void alarmArmedAway_sensorActivated_alarmStatusPutToPending_alarmStatusToAlarm() {
        doReturn(ArmingStatus.ARMED_AWAY).when(securityRepository).getArmingStatus();
        doReturn(AlarmStatus.PENDING_ALARM).when(securityRepository).getAlarmStatus();
        securityService.changeSensorActivationStatus(sensor_window, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }
    @Test
    @DisplayName("Test #2.2 - ArmingStatus: ARMED_HOME")
    public void alarmArmedHome_sensorActivated_alarmStatusPutToPending_alarmStatusToAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor_window, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }
    //#3. Pending alarm and all sensors are inactive, return to no alarm state.
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"PENDING_ALARM"})
    @DisplayName("Test #3")
    public void pendingAlarmedAndAllSensorsInactived_returnNoAlarmState(AlarmStatus alarmStatus) {
        doReturn(alarmStatus).when(securityRepository).getAlarmStatus();
        sensor_window.setActive(true);
        securityService.changeSensorActivationStatus(sensor_window, false);

        if (alarmStatus == AlarmStatus.PENDING_ALARM) {
            verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
        }
    }
    //#4 Alarm is active, change in sensor state should not affect the alarm state.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Test #4")
    public void alarmActiveAndChangeSensorState_shouldNotAffectAlarmState(boolean status_sensor_window) {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        sensor_window.setActive(status_sensor_window);
        securityService.changeSensorActivationStatus(sensor_window, status_sensor_window);
        assertEquals(securityService.getAlarmStatus(), AlarmStatus.ALARM);
    }

    //#5 A sensor is activated while already active and the system is in pending state, change it to alarm state.
    @Test
    @DisplayName("Test #5")
    public void sensorActivatedAndAlreadyActiveAndSystemPendingState_changeToAlarmState() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        Sensor sensor = new Sensor("Door", SensorType.DOOR);
        sensor.setActive(Boolean.TRUE);
        Sensor sensor2 = new Sensor("Window", SensorType.WINDOW);
        securityService.changeSensorActivationStatus(sensor2, true);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    //#6 A sensor is deactivated while already inactive, make no changes to the alarm state.
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"ALARM", "NO_ALARM"})
    @DisplayName("Test #6")
    public void sensorDeactivatedAndWhileAlreadyInactive_makeNoChangeTheAlarmState(AlarmStatus alarmStatus) {
        sensor_window.setActive(Boolean.FALSE);
        when(securityRepository.getAlarmStatus()).thenReturn(alarmStatus);
        securityService.changeSensorActivationStatus(sensor_window, false);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    //7# If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.
    @Test
    @DisplayName("Test #7")
    public void identifiesAnImageACat_whileSystemArmedHome_putSystemIntoAlarm() {
//        securityRepository.setArmingStatus(ArmingStatus.ARMED_HOME);
        doReturn(true).when(imageService).imageContainsCat(any(), anyFloat());
        doReturn(ArmingStatus.ARMED_HOME).when(securityRepository).getArmingStatus();
        securityService.processImage(img);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    //#8 If the image service identifies an image that does not contain a cat, change the status to no alarm as long as the sensors are not active.
    @Test
    @DisplayName("Test #8")
    public void identifiesAnImageACat_NotContainACat_changeStatusToNoAlarm_asLongAsSensorsAreNotActive() {
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
//        doReturn(ArmingStatus.ARMED_HOME).when(securityRepository).getArmingStatus();
        sensor_window.setActive(Boolean.FALSE);
        securityService.processImage(img);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    //#9 If the system is disarmed, set the status to no alarm.
    @Test
    @DisplayName("Test #9")
    public void systemDisarmed_setAlarmStatusNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);

    }


    //#10 If the system is armed, reset all sensors to inactive.
    @ParameterizedTest
    @ValueSource(strings = {"ARMED_AWAY", "ARMED_HOME"})
    @DisplayName("Test #10")
    public void systemArmed_allSensorsInactive(String alarmingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        securityService.changeSensorActivationStatus(sensor_door, true);
        securityService.changeSensorActivationStatus(sensor_window, true);
        securityService.changeSensorActivationStatus(sensor_motion, true);
        securityService.setArmingStatus(ArmingStatus.valueOf(alarmingStatus));
        assertEquals(0, securityService.getActiveSensors().size());
    }

    //#11 If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
    @Test
    @DisplayName("Test #11")
    public void systemArmedHome_and_whileCameraShowsACat_setAlarmToAlarm() {
        doReturn(true).when(imageService).imageContainsCat(any(), anyFloat());
        doReturn(ArmingStatus.DISARMED).when(securityRepository).getArmingStatus();
        securityService.processImage(img);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }


    @Test
    @DisplayName("Test_getActiveSensors")
    public void sensorActivated_getActiveSensor_sensorAdded() {
        doReturn(Set.of(sensor_door)).when(securityRepository).getSensors();
        sensor_door.setActive(true);
        assertEquals(1, securityService.getActiveSensors().size());
    }


    @Test
    @DisplayName("Test_setFalseActivationStatusForSensors")
    public void Test_setFalseActivationStatusForSensors() {
        doReturn(Set.of(sensor_door)).when(securityRepository).getSensors();
        doReturn(AlarmStatus.NO_ALARM).when(securityRepository).getAlarmStatus();
        sensor_door.setActive(true);
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);
        assertEquals(Boolean.FALSE, sensor_door.getActive());

    }

    @Test
    void ifAlarmIsActive_SystemDisarmed_changeStatusToPending() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor_window,false);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }


}
