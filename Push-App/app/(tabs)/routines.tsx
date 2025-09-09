import React, { useState } from 'react';
import { StyleSheet, ScrollView, View, TouchableOpacity, Switch, Modal, Platform } from 'react-native';
import DateTimePicker from '@react-native-community/datetimepicker';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';
import { Colors } from '@/constants/Colors';
import { useColorScheme } from '@/hooks/useColorScheme';

// Mock app data for selection
const availableApps = [
  { id: '1', name: 'Instagram', icon: '📷' },
  { id: '2', name: 'TikTok', icon: '🎵' },
  { id: '3', name: 'Twitter', icon: '🐦' },
  { id: '4', name: 'YouTube', icon: '📺' },
  { id: '5', name: 'Facebook', icon: '📘' },
  { id: '6', name: 'WhatsApp', icon: '💬' },
  { id: '7', name: 'Snapchat', icon: '👻' },
  { id: '8', name: 'Reddit', icon: '🤖' },
];

interface RoutineSettings {
  enabled: boolean;
  time: Date;
  pushupRequirement: number;
  selectedApps: string[];
}

export default function RoutinesScreen() {
  const colorScheme = useColorScheme();
  const colors = Colors[colorScheme ?? 'light'];
  
  const [morningRoutine, setMorningRoutine] = useState<RoutineSettings>({
    enabled: false,
    time: new Date(2024, 0, 1, 6, 0), // 6:00 AM
    pushupRequirement: 10,
    selectedApps: ['1', '2'], // Instagram, TikTok
  });
  
  const [midnightRoutine, setMidnightRoutine] = useState<RoutineSettings>({
    enabled: true,
    time: new Date(2024, 0, 1, 22, 0), // 10:00 PM
    pushupRequirement: 15,
    selectedApps: ['1', '2', '3'], // Instagram, TikTok, Twitter
  });

  const [showTimePicker, setShowTimePicker] = useState<'morning' | 'midnight' | null>(null);
  const [showAppSelector, setShowAppSelector] = useState<'morning' | 'midnight' | null>(null);

  const formatTime = (date: Date) => {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const updateRoutine = (type: 'morning' | 'midnight', updates: Partial<RoutineSettings>) => {
    if (type === 'morning') {
      setMorningRoutine(prev => ({ ...prev, ...updates }));
    } else {
      setMidnightRoutine(prev => ({ ...prev, ...updates }));
    }
  };

  const toggleAppSelection = (routineType: 'morning' | 'midnight', appId: string) => {
    const routine = routineType === 'morning' ? morningRoutine : midnightRoutine;
    const selectedApps = routine.selectedApps.includes(appId)
      ? routine.selectedApps.filter(id => id !== appId)
      : [...routine.selectedApps, appId];
    
    updateRoutine(routineType, { selectedApps });
  };

  const onTimeChange = (event: any, selectedTime?: Date) => {
    if (Platform.OS === 'android') {
      setShowTimePicker(null);
    }
    
    if (selectedTime && showTimePicker) {
      updateRoutine(showTimePicker, { time: selectedTime });
    }
  };

  const renderRoutineCard = (
    type: 'morning' | 'midnight',
    routine: RoutineSettings,
    title: string,
    icon: string
  ) => (
    <ThemedView style={[styles.routineCard, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
      <View style={styles.routineHeader}>
        <View style={styles.routineTitleContainer}>
          <ThemedText style={styles.routineIcon}>{icon}</ThemedText>
          <ThemedText type="subtitle" style={[styles.routineTitle, { color: colors.text }]}>
            {title}
          </ThemedText>
        </View>
        <Switch
          value={routine.enabled}
          onValueChange={(enabled) => updateRoutine(type, { enabled })}
          trackColor={{ false: colors.border, true: colors.primaryAccent }}
          thumbColor={routine.enabled ? colors.cardBackground : colors.textSecondary}
        />
      </View>

      {routine.enabled && (
        <View style={styles.routineSettings}>
          {/* Time Picker */}
          <View style={styles.settingRow}>
            <ThemedText type="defaultSemiBold" style={[styles.settingLabel, { color: colors.text }]}>
              Start Time
            </ThemedText>
            <TouchableOpacity
              style={[styles.timeButton, { backgroundColor: colors.secondaryAccent }]}
              onPress={() => setShowTimePicker(type)}
            >
              <IconSymbol name="clock" size={16} color={colors.cardBackground} style={styles.timeIcon} />
              <ThemedText style={[styles.timeText, { color: colors.cardBackground }]}>
                {formatTime(routine.time)}
              </ThemedText>
            </TouchableOpacity>
          </View>

          {/* Push-up Requirement */}
          <View style={styles.settingRow}>
            <ThemedText type="defaultSemiBold" style={[styles.settingLabel, { color: colors.text }]}>
              Push-ups Required
            </ThemedText>
            <View style={styles.numberPicker}>
              <TouchableOpacity
                style={[styles.numberButton, { backgroundColor: colors.actionHighlight }]}
                onPress={() => updateRoutine(type, { pushupRequirement: Math.max(1, routine.pushupRequirement - 1) })}
              >
                <ThemedText style={[styles.numberButtonText, { color: colors.cardBackground }]}>-</ThemedText>
              </TouchableOpacity>
              <ThemedText type="title" style={[styles.numberValue, { color: colors.text }]}>
                {routine.pushupRequirement}
              </ThemedText>
              <TouchableOpacity
                style={[styles.numberButton, { backgroundColor: colors.actionHighlight }]}
                onPress={() => updateRoutine(type, { pushupRequirement: routine.pushupRequirement + 1 })}
              >
                <ThemedText style={[styles.numberButtonText, { color: colors.cardBackground }]}>+</ThemedText>
              </TouchableOpacity>
            </View>
          </View>

          {/* Selected Apps */}
          <View style={styles.settingRow}>
            <ThemedText type="defaultSemiBold" style={[styles.settingLabel, { color: colors.text }]}>
              Restricted Apps ({routine.selectedApps.length})
            </ThemedText>
            <TouchableOpacity
              style={[styles.selectAppsButton, { backgroundColor: colors.primaryAccent }]}
              onPress={() => setShowAppSelector(type)}
            >
              <ThemedText style={[styles.selectAppsText, { color: colors.cardBackground }]}>
                Select Apps
              </ThemedText>
            </TouchableOpacity>
          </View>

          {/* Selected Apps Preview */}
          {routine.selectedApps.length > 0 && (
            <View style={styles.selectedAppsPreview}>
              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                {routine.selectedApps.map(appId => {
                  const app = availableApps.find(a => a.id === appId);
                  return app ? (
                    <View key={appId} style={[styles.selectedAppItem, { backgroundColor: colors.surface, borderColor: colors.border }]}>
                      <ThemedText style={styles.selectedAppIcon}>{app.icon}</ThemedText>
                      <ThemedText style={[styles.selectedAppName, { color: colors.text }]} numberOfLines={1}>
                        {app.name}
                      </ThemedText>
                    </View>
                  ) : null;
                })}
              </ScrollView>
            </View>
          )}
        </View>
      )}
    </ThemedView>
  );

  return (
    <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        {/* Header */}
        <ThemedView style={[styles.header, { backgroundColor: colors.background }]}>
          <ThemedText type="title" style={[styles.headerTitle, { color: colors.text }]}>
            Routines
          </ThemedText>
          <ThemedText type="default" style={[styles.subtitle, { color: colors.textSecondary }]}>
            Configure your morning and midnight routines
          </ThemedText>
        </ThemedView>

        {/* Routines */}
        <ThemedView style={styles.routinesContainer}>
          {renderRoutineCard('morning', morningRoutine, 'Morning Routine', '🌅')}
          {renderRoutineCard('midnight', midnightRoutine, 'Midnight Routine', '🌙')}
        </ThemedView>

        {/* Info Section */}
        <ThemedView style={[styles.infoCard, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
          <IconSymbol name="info.circle" size={24} color={colors.secondaryAccent} style={styles.infoIcon} />
          <View style={styles.infoContent}>
            <ThemedText type="defaultSemiBold" style={[styles.infoTitle, { color: colors.text }]}>
              How Routines Work
            </ThemedText>
            <ThemedText type="default" style={[styles.infoText, { color: colors.textSecondary }]}>
              During routine hours, selected apps will be restricted. Complete the required push-ups to unlock them temporarily.
            </ThemedText>
          </View>
        </ThemedView>
      </ScrollView>

      {/* Time Picker Modal */}
      {showTimePicker && (
        <Modal
          animationType="slide"
          transparent={true}
          visible={true}
          onRequestClose={() => setShowTimePicker(null)}
        >
          <View style={styles.modalOverlay}>
            <ThemedView style={[styles.timePickerModal, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
              <View style={styles.modalHeader}>
                <ThemedText type="subtitle" style={[styles.modalTitle, { color: colors.text }]}>
                  Select Time
                </ThemedText>
                <TouchableOpacity onPress={() => setShowTimePicker(null)}>
                  <IconSymbol name="xmark" size={24} color={colors.icon} />
                </TouchableOpacity>
              </View>
              <DateTimePicker
                value={showTimePicker === 'morning' ? morningRoutine.time : midnightRoutine.time}
                mode="time"
                display="spinner"
                onChange={onTimeChange}
                style={styles.timePicker}
              />
              {Platform.OS === 'ios' && (
                <TouchableOpacity
                  style={[styles.doneButton, { backgroundColor: colors.primaryAccent }]}
                  onPress={() => setShowTimePicker(null)}
                >
                  <ThemedText style={[styles.doneButtonText, { color: colors.cardBackground }]}>Done</ThemedText>
                </TouchableOpacity>
              )}
            </ThemedView>
          </View>
        </Modal>
      )}

      {/* App Selector Modal */}
      {showAppSelector && (
        <Modal
          animationType="slide"
          transparent={true}
          visible={true}
          onRequestClose={() => setShowAppSelector(null)}
        >
          <View style={styles.modalOverlay}>
            <ThemedView style={[styles.appSelectorModal, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
              <View style={styles.modalHeader}>
                <ThemedText type="subtitle" style={[styles.modalTitle, { color: colors.text }]}>
                  Select Apps to Restrict
                </ThemedText>
                <TouchableOpacity onPress={() => setShowAppSelector(null)}>
                  <IconSymbol name="xmark" size={24} color={colors.icon} />
                </TouchableOpacity>
              </View>
              
              <ScrollView style={styles.appSelectorList}>
                {availableApps.map(app => {
                  const routine = showAppSelector === 'morning' ? morningRoutine : midnightRoutine;
                  const isSelected = routine.selectedApps.includes(app.id);
                  
                  return (
                    <TouchableOpacity
                      key={app.id}
                      style={[
                        styles.appSelectorItem,
                        {
                          backgroundColor: isSelected ? colors.primaryAccent + '20' : 'transparent',
                          borderColor: colors.border,
                        }
                      ]}
                      onPress={() => toggleAppSelection(showAppSelector, app.id)}
                    >
                      <View style={styles.appSelectorInfo}>
                        <ThemedText style={styles.appSelectorIcon}>{app.icon}</ThemedText>
                        <ThemedText type="defaultSemiBold" style={[styles.appSelectorName, { color: colors.text }]}>
                          {app.name}
                        </ThemedText>
                      </View>
                      <View style={[
                        styles.checkbox,
                        {
                          borderColor: isSelected ? colors.primaryAccent : colors.border,
                          backgroundColor: isSelected ? colors.primaryAccent : 'transparent',
                        }
                      ]}>
                        {isSelected && (
                          <IconSymbol name="checkmark" size={16} color={colors.cardBackground} />
                        )}
                      </View>
                    </TouchableOpacity>
                  );
                })}
              </ScrollView>
              
              <TouchableOpacity
                style={[styles.doneButton, { backgroundColor: colors.primaryAccent }]}
                onPress={() => setShowAppSelector(null)}
              >
                <ThemedText style={[styles.doneButtonText, { color: colors.cardBackground }]}>Done</ThemedText>
              </TouchableOpacity>
            </ThemedView>
          </View>
        </Modal>
      )}
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  scrollView: {
    flex: 1,
  },
  header: {
    paddingTop: 60,
    paddingHorizontal: 20,
    paddingBottom: 20,
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: 'bold',
  },
  subtitle: {
    marginTop: 8,
    fontSize: 16,
  },
  routinesContainer: {
    padding: 20,
    gap: 20,
  },
  routineCard: {
    borderRadius: 16,
    padding: 20,
    borderWidth: 1,
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  routineHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  routineTitleContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  routineIcon: {
    fontSize: 24,
    marginRight: 12,
  },
  routineTitle: {
    fontSize: 20,
    fontWeight: '600',
  },
  routineSettings: {
    gap: 16,
  },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  settingLabel: {
    fontSize: 16,
    flex: 1,
  },
  timeButton: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
  },
  timeIcon: {
    marginRight: 8,
  },
  timeText: {
    fontSize: 16,
    fontWeight: '600',
  },
  numberPicker: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  numberButton: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  numberButtonText: {
    fontSize: 20,
    fontWeight: 'bold',
  },
  numberValue: {
    fontSize: 20,
    fontWeight: 'bold',
    minWidth: 40,
    textAlign: 'center',
  },
  selectAppsButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
  },
  selectAppsText: {
    fontSize: 14,
    fontWeight: '600',
  },
  selectedAppsPreview: {
    marginTop: 8,
  },
  selectedAppItem: {
    alignItems: 'center',
    padding: 8,
    borderRadius: 8,
    borderWidth: 1,
    marginRight: 8,
    minWidth: 60,
  },
  selectedAppIcon: {
    fontSize: 20,
    marginBottom: 4,
  },
  selectedAppName: {
    fontSize: 10,
    textAlign: 'center',
  },
  infoCard: {
    margin: 20,
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'flex-start',
  },
  infoIcon: {
    marginRight: 12,
    marginTop: 2,
  },
  infoContent: {
    flex: 1,
  },
  infoTitle: {
    fontSize: 16,
    marginBottom: 4,
  },
  infoText: {
    fontSize: 14,
    lineHeight: 20,
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  timePickerModal: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 20,
    minWidth: 300,
  },
  appSelectorModal: {
    borderRadius: 16,
    borderWidth: 1,
    maxHeight: '80%',
    width: '90%',
  },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 20,
    paddingHorizontal: 20,
    paddingTop: 20,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '600',
  },
  timePicker: {
    marginVertical: 20,
  },
  appSelectorList: {
    flex: 1,
    paddingHorizontal: 20,
  },
  appSelectorItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 8,
  },
  appSelectorInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  appSelectorIcon: {
    fontSize: 24,
    marginRight: 12,
  },
  appSelectorName: {
    fontSize: 16,
  },
  checkbox: {
    width: 24,
    height: 24,
    borderRadius: 4,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  doneButton: {
    margin: 20,
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  doneButtonText: {
    fontSize: 16,
    fontWeight: '600',
  },
});