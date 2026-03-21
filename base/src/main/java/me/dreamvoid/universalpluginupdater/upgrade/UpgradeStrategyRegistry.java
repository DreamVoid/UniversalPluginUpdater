package me.dreamvoid.universalpluginupdater.upgrade;

import me.dreamvoid.universalpluginupdater.Utils;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 升级策略全局注册表
 * 管理所有可用的升级策略
 */
public class UpgradeStrategyRegistry {
    private static final UpgradeStrategyRegistry INSTANCE = new UpgradeStrategyRegistry();
    
    private final Map<String, IUpgradeStrategy> strategies = new HashMap<>();
    private String activeStrategy = "native"; // 默认使用 native 策略
    
    private final Logger logger = Utils.getLogger();

    private UpgradeStrategyRegistry() {
    }

    public static UpgradeStrategyRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 注册一个升级策略
     * @param strategyId 策略标识符
     * @param strategy 策略实现
     */
    public void registerStrategy(String strategyId, IUpgradeStrategy strategy) {
        if (strategyId == null || strategy == null) {
            return;
        }
        
        strategies.put(strategyId, strategy);
        
        if (logger != null) {
            logger.info("注册新的升级方式: " + strategyId + " (" + strategy.getDisplayName() + ")");
        }
    }

    /**
     * 获取指定标识符的升级策略
     * @param strategyId 策略标识符
     * @return 策略实现，如果不存在返回null
     */
    @Nullable
    public IUpgradeStrategy getStrategy(String strategyId) {
        return strategies.get(strategyId);
    }

    /**
     * 设置当前活跃的升级策略
     * 允许设置尚未注册的策略，因为策略可能在后续才被注册
     * @param strategyId 策略标识符
     */
    public void setActiveStrategy(String strategyId) {
        if (strategyId == null) {
            return;
        }
        
        this.activeStrategy = strategyId;
        
        if (logger != null) {
            // 如果策略已注册，可以获取显示名，否则仅显示标识符
            IUpgradeStrategy strategy = strategies.get(strategyId);
            if (strategy != null) {
                logger.info("当前使用的升级策略: " + strategyId + " (" + strategy.getDisplayName() + ")");
            } else {
                logger.info("当前使用的升级策略: " + strategyId + " (未注册)");
            }
        }
    }

    /**
     * 获取当前活跃的升级策略
     * 如果当前活跃策略不存在，自动回退到 native 策略
     * @return 当前活跃的升级策略（保证不为 null）
     */
    @Nullable
    public IUpgradeStrategy getActiveStrategy() {
        // 检查当前活跃策略是否存在
        IUpgradeStrategy strategy = strategies.get(activeStrategy);
        
        // 如果不存在且不是 native，则回退到 native
        if (strategy == null && !"native".equals(activeStrategy)) {
            if (logger != null) {
                logger.warning("当前使用的升级策略 '" + activeStrategy + "' 未注册，回退到 'native'");
            }
            this.activeStrategy = "native";
            strategy = strategies.get("native");
        }
        
        return strategy;
    }

    /**
     * 获取当前活跃的升级策略标识符
     * @return 策略标识符
     */
    public String getActiveStrategyId() {
        return activeStrategy;
    }
}
