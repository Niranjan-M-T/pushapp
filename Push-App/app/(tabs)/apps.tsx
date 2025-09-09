import React, { useState, useEffect } from 'react';
import { StyleSheet, ScrollView, View, TouchableOpacity, TextInput, Switch, Modal, Alert } from 'react-native';
import { IconSymbol } from '@/components/ui/IconSymbol';
import { ThemedText } from '@/components/ThemedText';
import { ThemedView } from '@/components/ThemedView';
import { Colors } from '@/constants/Colors';
import { useColorScheme } from '@/hooks/useColorScheme';
import AppUsageService, { AppSettings, InstalledApp } from '@/services/AppUsageService';

// Mock app data - in real implementation, this would come from device
const mockInstalledApps = [
  { id: '1', name: 'Instagram', icon: '📷', usage: '2h 15m', isTracked: true, timeLimit: 30, pushupRequirement: 10, enforcement: 'overlay' },
  { id: '2', name: 'TikTok', icon: '🎵', usage: '1h 45m', isTracked: true, timeLimit: 45, pushupRequirement: 15, enforcement: 'disable' },
  { id: '3', name: 'Twitter', icon: '🐦', usage: '45m', isTracked: true, timeLimit: 60, pushupRequirement: 8, enforcement: 'popup' },
  { id: '4', name: 'YouTube', icon: '📺', usage: '30m', isTracked: false, timeLimit: 120, pushupRequirement: 20, enforcement: 'overlay' },
  { id: '5', name: 'Facebook', icon: '📘', usage: '15m', isTracked: false, timeLimit: 30, pushupRequirement: 12, enforcement: 'disable' },
  { id: '6', name: 'WhatsApp', icon: '💬', usage: '1h 20m', isTracked: false, timeLimit: 180, pushupRequirement: 5, enforcement: 'popup' },
  { id: '7', name: 'Snapchat', icon: '👻', usage: '25m', isTracked: false, timeLimit: 45, pushupRequirement: 10, enforcement: 'overlay' },
  { id: '8', name: 'Reddit', icon: '🤖', usage: '40m', isTracked: false, timeLimit: 90, pushupRequirement: 15, enforcement: 'disable' },
];

interface AppSettings {
  id: string;
  name: string;
  icon: string;
  usage: string;
  isTracked: boolean;
  timeLimit: number;
  pushupRequirement: number;
  enforcement: 'disable' | 'overlay' | 'popup';
}

export default function AppsScreen() {
  const colorScheme = useColorScheme();
  const colors = Colors[colorScheme ?? 'light'];
  const [apps, setApps] = useState<AppSettings[]>([]);
  const [installedApps, setInstalledApps] = useState<InstalledApp[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedApp, setSelectedApp] = useState<AppSettings | null>(null);
  const [modalVisible, setModalVisible] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    loadAppsData();
  }, []);

  const loadAppsData = async () => {
    try {
      setIsLoading(true);
      
      // Load installed apps
      const installed = await AppUsageService.getInstalledApps();
      setInstalledApps(installed);
      
      // Load app settings
      const settings = await AppUsageService.loadAppSettings();
      
      // Merge installed apps with settings
      const mergedApps = await Promise.all(
        installed.map(async (app) => {
          const existingSettings = settings.find(s => s.packageName === app.packageName);
          const stats = await AppUsageService.getAppUsageStats(app.packageName);
          const usageMinutes = stats ? Math.round(stats.totalTimeInForeground / (1000 * 60)) : 0;
          const hours = Math.floor(usageMinutes / 60);
          const mins = usageMinutes % 60;
          const usage = hours > 0 ? `${hours}h ${mins}m` : `${mins}m`;
          
          return {
            id: app.packageName,
            name: app.appName,
            icon: app.icon,
            usage,
            isTracked: existingSettings?.isTracked || false,
            timeLimit: existingSettings?.timeLimit || 60,
            pushupRequirement: existingSettings?.pushupRequirement || 10,
            enforcement: existingSettings?.enforcement || 'overlay',
            packageName: app.packageName,
            isBlocked: existingSettings?.isBlocked || false,
            unlockTime: existingSettings?.unlockTime,
            unlockDuration: existingSettings?.unlockDuration || 60,
          } as AppSettings;
        })
      );
      
      setApps(mergedApps);
    } catch (error) {
      console.error('Failed to load apps data:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const filteredApps = apps.filter(app => 
    app.name.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const toggleAppTracking = (appId: string) => {
    setApps(prevApps => 
      prevApps.map(app => 
        app.id === appId ? { ...app, isTracked: !app.isTracked } : app
      )
    );
  };

  const openAppSettings = (app: AppSettings) => {
    setSelectedApp({ ...app });
    setModalVisible(true);
  };

  const saveAppSettings = async () => {
    if (!selectedApp) return;

    try {
      // Update app settings in the service
      await AppUsageService.updateAppSettings(selectedApp.packageName, {
        isTracked: selectedApp.isTracked,
        timeLimit: selectedApp.timeLimit,
        pushupRequirement: selectedApp.pushupRequirement,
        enforcement: selectedApp.enforcement,
      });

      // Update local state
      setApps(prevApps => 
        prevApps.map(app => 
          app.id === selectedApp.id ? selectedApp : app
        )
      );
      
      setModalVisible(false);
      setSelectedApp(null);
      
      Alert.alert('Success', 'App settings saved successfully!');
    } catch (error) {
      console.error('Failed to save app settings:', error);
      Alert.alert('Error', 'Failed to save app settings. Please try again.');
    }
  };

  const updateSelectedApp = (updates: Partial<AppSettings>) => {
    if (!selectedApp) return;
    setSelectedApp({ ...selectedApp, ...updates });
  };

  const enforcementOptions = [
    { value: 'disable', label: 'Disable App', description: 'Completely disable the app when limit is reached' },
    { value: 'overlay', label: 'Persistent Overlay', description: 'Show overlay that requires push-ups to dismiss' },
    { value: 'popup', label: 'Pop-up Notification', description: 'Show notification with push-up challenge' },
  ];

  return (
    <ThemedView style={[styles.container, { backgroundColor: colors.background }]}>
      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        {/* Header */}
        <ThemedView style={[styles.header, { backgroundColor: colors.background }]}>
          <ThemedText type="title" style={[styles.headerTitle, { color: colors.text }]}>
            App Management
          </ThemedText>
          <ThemedText type="default" style={[styles.subtitle, { color: colors.textSecondary }]}>
            Manage your app limits and push-up requirements
          </ThemedText>
        </ThemedView>

        {/* Search Bar */}
        <ThemedView style={styles.searchContainer}>
          <View style={[styles.searchBar, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
            <IconSymbol name="magnifyingglass" size={20} color={colors.icon} style={styles.searchIcon} />
            <TextInput
              style={[styles.searchInput, { color: colors.text }]}
              placeholder="Search apps..."
              placeholderTextColor={colors.textSecondary}
              value={searchQuery}
              onChangeText={setSearchQuery}
            />
          </View>
        </ThemedView>

        {/* Apps List */}
        <ThemedView style={styles.appsContainer}>
          {filteredApps.map((app) => (
            <TouchableOpacity
              key={app.id}
              style={[
                styles.appRow,
                { backgroundColor: colors.cardBackground, borderColor: colors.border }
              ]}
              onPress={() => app.isTracked && openAppSettings(app)}
              disabled={!app.isTracked}
            >
              <View style={styles.appInfo}>
                <ThemedText style={styles.appIcon}>{app.icon}</ThemedText>
                <View style={styles.appDetails}>
                  <ThemedText type="defaultSemiBold" style={[styles.appName, { color: colors.text }]}>
                    {app.name}
                  </ThemedText>
                  <ThemedText type="default" style={[styles.appUsage, { color: colors.textSecondary }]}>
                    {app.usage} today
                  </ThemedText>
                </View>
              </View>
              
              <View style={styles.appControls}>
                {app.isTracked && (
                  <View style={styles.limitInfo}>
                    <ThemedText type="default" style={[styles.limitText, { color: colors.textSecondary }]}>
                      {app.timeLimit}m limit
                    </ThemedText>
                    <ThemedText type="default" style={[styles.pushupText, { color: colors.primaryAccent }]}>
                      {app.pushupRequirement} push-ups
                    </ThemedText>
                  </View>
                )}
                <Switch
                  value={app.isTracked}
                  onValueChange={() => toggleAppTracking(app.id)}
                  trackColor={{ false: colors.border, true: colors.primaryAccent }}
                  thumbColor={app.isTracked ? colors.cardBackground : colors.textSecondary}
                />
              </View>
            </TouchableOpacity>
          ))}
        </ThemedView>
      </ScrollView>

      {/* App Settings Modal */}
      <Modal
        animationType="slide"
        transparent={true}
        visible={modalVisible}
        onRequestClose={() => setModalVisible(false)}
      >
        <View style={styles.modalOverlay}>
          <ThemedView style={[styles.modalContent, { backgroundColor: colors.cardBackground, borderColor: colors.border }]}>
            {selectedApp && (
              <>
                <View style={styles.modalHeader}>
                  <View style={styles.modalTitleContainer}>
                    <ThemedText style={styles.modalAppIcon}>{selectedApp.icon}</ThemedText>
                    <ThemedText type="subtitle" style={[styles.modalTitle, { color: colors.text }]}>
                      {selectedApp.name}
                    </ThemedText>
                  </View>
                  <TouchableOpacity onPress={() => setModalVisible(false)}>
                    <IconSymbol name="xmark" size={24} color={colors.icon} />
                  </TouchableOpacity>
                </View>

                <ScrollView style={styles.modalBody}>
                  {/* Time Limit Slider */}
                  <View style={styles.settingSection}>
                    <ThemedText type="defaultSemiBold" style={[styles.settingLabel, { color: colors.text }]}>
                      Daily Time Limit
                    </ThemedText>
                    <View style={styles.sliderContainer}>
                      <ThemedText type="title" style={[styles.sliderValue, { color: colors.primaryAccent }]}>
                        {selectedApp.timeLimit} minutes
                      </ThemedText>
                      <View style={styles.sliderButtons}>
                        <TouchableOpacity
                          style={[styles.sliderButton, { backgroundColor: colors.secondaryAccent }]}
                          onPress={() => updateSelectedApp({ timeLimit: Math.max(15, selectedApp.timeLimit - 15) })}
                        >
                          <ThemedText style={[styles.sliderButtonText, { color: colors.cardBackground }]}>-15</ThemedText>
                        </TouchableOpacity>
                        <TouchableOpacity
                          style={[styles.sliderButton, { backgroundColor: colors.secondaryAccent }]}
                          onPress={() => updateSelectedApp({ timeLimit: selectedApp.timeLimit + 15 })}
                        >
                          <ThemedText style={[styles.sliderButtonText, { color: colors.cardBackground }]}>+15</ThemedText>
                        </TouchableOpacity>
                      </View>
                    </View>
                  </View>

                  {/* Push-up Requirement */}
                  <View style={styles.settingSection}>
                    <ThemedText type="defaultSemiBold" style={[styles.settingLabel, { color: colors.text }]}>
                      Push-up Requirement
                    </ThemedText>
                    <View style={styles.numberPickerContainer}>
                      <TouchableOpacity
                        style={[styles.numberButton, { backgroundColor: colors.actionHighlight }]}
                        onPress={() => updateSelectedApp({ pushupRequirement: Math.max(1, selectedApp.pushupRequirement - 1) })}
                      >
                        <ThemedText style={[styles.numberButtonText, { color: colors.cardBackground }]}>-</ThemedText>
                      </TouchableOpacity>
                      <ThemedText type="title" style={[styles.numberValue, { color: colors.text }]}>
                        {selectedApp.pushupRequirement}
                      </ThemedText>
                      <TouchableOpacity
                        style={[styles.numberButton, { backgroundColor: colors.actionHighlight }]}
                        onPress={() => updateSelectedApp({ pushupRequirement: selectedApp.pushupRequirement + 1 })}
                      >
                        <ThemedText style={[styles.numberButtonText, { color: colors.cardBackground }]}>+</ThemedText>
                      </TouchableOpacity>
                    </View>
                  </View>

                  {/* Enforcement Method */}
                  <View style={styles.settingSection}>
                    <ThemedText type="defaultSemiBold" style={[styles.settingLabel, { color: colors.text }]}>
                      Enforcement Method
                    </ThemedText>
                    {enforcementOptions.map((option) => (
                      <TouchableOpacity
                        key={option.value}
                        style={[
                          styles.enforcementOption,
                          {
                            backgroundColor: selectedApp.enforcement === option.value ? colors.primaryAccent + '20' : 'transparent',
                            borderColor: selectedApp.enforcement === option.value ? colors.primaryAccent : colors.border,
                          }
                        ]}
                        onPress={() => updateSelectedApp({ enforcement: option.value as any })}
                      >
                        <View style={styles.enforcementOptionContent}>
                          <ThemedText type="defaultSemiBold" style={[styles.enforcementLabel, { color: colors.text }]}>
                            {option.label}
                          </ThemedText>
                          <ThemedText type="default" style={[styles.enforcementDescription, { color: colors.textSecondary }]}>
                            {option.description}
                          </ThemedText>
                        </View>
                        <View style={[
                          styles.radioButton,
                          {
                            borderColor: selectedApp.enforcement === option.value ? colors.primaryAccent : colors.border,
                            backgroundColor: selectedApp.enforcement === option.value ? colors.primaryAccent : 'transparent',
                          }
                        ]}>
                          {selectedApp.enforcement === option.value && (
                            <View style={[styles.radioButtonInner, { backgroundColor: colors.cardBackground }]} />
                          )}
                        </View>
                      </TouchableOpacity>
                    ))}
                  </View>
                </ScrollView>

                {/* Modal Actions */}
                <View style={styles.modalActions}>
                  <TouchableOpacity
                    style={[styles.modalButton, styles.cancelButton, { borderColor: colors.border }]}
                    onPress={() => setModalVisible(false)}
                  >
                    <ThemedText style={[styles.modalButtonText, { color: colors.textSecondary }]}>Cancel</ThemedText>
                  </TouchableOpacity>
                  <TouchableOpacity
                    style={[styles.modalButton, styles.saveButton, { backgroundColor: colors.primaryAccent }]}
                    onPress={saveAppSettings}
                  >
                    <ThemedText style={[styles.modalButtonText, { color: colors.cardBackground }]}>Save</ThemedText>
                  </TouchableOpacity>
                </View>
              </>
            )}
          </ThemedView>
        </View>
      </Modal>
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
  searchContainer: {
    paddingHorizontal: 20,
    marginBottom: 20,
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 12,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  searchIcon: {
    marginRight: 12,
  },
  searchInput: {
    flex: 1,
    fontSize: 16,
  },
  appsContainer: {
    paddingHorizontal: 20,
    gap: 12,
  },
  appRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
  },
  appInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  appIcon: {
    fontSize: 32,
    marginRight: 16,
  },
  appDetails: {
    flex: 1,
  },
  appName: {
    fontSize: 16,
    marginBottom: 4,
  },
  appUsage: {
    fontSize: 14,
  },
  appControls: {
    alignItems: 'flex-end',
    gap: 8,
  },
  limitInfo: {
    alignItems: 'flex-end',
  },
  limitText: {
    fontSize: 12,
  },
  pushupText: {
    fontSize: 12,
    fontWeight: '600',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'flex-end',
  },
  modalContent: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    borderWidth: 1,
    maxHeight: '80%',
  },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 20,
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
  },
  modalTitleContainer: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  modalAppIcon: {
    fontSize: 24,
    marginRight: 12,
  },
  modalTitle: {
    fontSize: 20,
    fontWeight: '600',
  },
  modalBody: {
    flex: 1,
    padding: 20,
  },
  settingSection: {
    marginBottom: 32,
  },
  settingLabel: {
    fontSize: 16,
    marginBottom: 16,
  },
  sliderContainer: {
    alignItems: 'center',
    gap: 16,
  },
  sliderValue: {
    fontSize: 24,
    fontWeight: 'bold',
  },
  sliderButtons: {
    flexDirection: 'row',
    gap: 16,
  },
  sliderButton: {
    paddingHorizontal: 20,
    paddingVertical: 10,
    borderRadius: 8,
  },
  sliderButtonText: {
    fontSize: 16,
    fontWeight: '600',
  },
  numberPickerContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 24,
  },
  numberButton: {
    width: 44,
    height: 44,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
  },
  numberButtonText: {
    fontSize: 24,
    fontWeight: 'bold',
  },
  numberValue: {
    fontSize: 32,
    fontWeight: 'bold',
    minWidth: 60,
    textAlign: 'center',
  },
  enforcementOption: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
    borderRadius: 12,
    borderWidth: 1,
    marginBottom: 12,
  },
  enforcementOptionContent: {
    flex: 1,
  },
  enforcementLabel: {
    fontSize: 16,
    marginBottom: 4,
  },
  enforcementDescription: {
    fontSize: 14,
    lineHeight: 20,
  },
  radioButton: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
  radioButtonInner: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  modalActions: {
    flexDirection: 'row',
    padding: 20,
    gap: 12,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
  },
  modalButton: {
    flex: 1,
    paddingVertical: 16,
    borderRadius: 12,
    alignItems: 'center',
  },
  cancelButton: {
    borderWidth: 1,
  },
  saveButton: {
    // backgroundColor set dynamically
  },
  modalButtonText: {
    fontSize: 16,
    fontWeight: '600',
  },
});