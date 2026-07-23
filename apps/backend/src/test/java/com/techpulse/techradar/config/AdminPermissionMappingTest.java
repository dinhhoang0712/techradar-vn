package com.techpulse.techradar.config;

import com.techpulse.techradar.features.clustering.adapters.input.AdminClusteringController;
import com.techpulse.techradar.features.graph.adapters.input.GraphAnalyticsAdminController;
import com.techpulse.techradar.features.notification.adapters.input.AdminNotificationController;
import com.techpulse.techradar.features.radar.adapters.input.AnalyticsAdminController;
import com.techpulse.techradar.features.system.adapters.input.AdminCmsController;
import com.techpulse.techradar.features.system.adapters.input.AdminController;
import com.techpulse.techradar.features.system.adapters.input.AdminDashboardController;
import com.techpulse.techradar.features.system.adapters.input.AdminDataPlatformController;
import com.techpulse.techradar.features.system.adapters.input.AdminSocialController;
import com.techpulse.techradar.features.system.adapters.input.AuditLogAdminController;
import com.techpulse.techradar.features.system.adapters.input.CacheAdminController;
import com.techpulse.techradar.features.system.adapters.input.CrawlerAdminController;
import com.techpulse.techradar.features.user.adapters.input.UserAdminController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the RBAC permission mapping (V24__rbac_permissions.sql): every method on
 * an admin controller must be gated by exactly the permission that migration grants to the
 * ADMIN role for that controller's area, via {@code hasAuthority(...)} - never a bare
 * {@code hasRole('ADMIN')} (which would bypass per-permission RBAC) and never the wrong code.
 */
class AdminPermissionMappingTest {

    private record ControllerPermission(Class<?> controller, String permissionCode) {
    }

    static Stream<ControllerPermission> controllerPermissions() {
        return Stream.of(
                new ControllerPermission(UserAdminController.class, "user:manage"),
                new ControllerPermission(AdminNotificationController.class, "notification:manage"),
                new ControllerPermission(AnalyticsAdminController.class, "analytics:manage"),
                new ControllerPermission(AdminCmsController.class, "cms:manage"),
                new ControllerPermission(CrawlerAdminController.class, "crawler:manage"),
                new ControllerPermission(CacheAdminController.class, "cache:manage"),
                new ControllerPermission(AdminController.class, "system:settings"),
                new ControllerPermission(AdminDataPlatformController.class, "datapipeline:manage"),
                new ControllerPermission(AdminSocialController.class, "social:moderate"),
                new ControllerPermission(AuditLogAdminController.class, "audit:view"),
                new ControllerPermission(AdminDashboardController.class, "dashboard:view"),
                new ControllerPermission(AdminClusteringController.class, "clustering:manage"),
                new ControllerPermission(GraphAnalyticsAdminController.class, "graph:manage")
        );
    }

    @ParameterizedTest
    @MethodSource("controllerPermissions")
    void everyPreAuthorizedMethod_requiresExactlyItsMappedPermission(ControllerPermission cp) {
        List<Method> annotated = Stream.of(cp.controller().getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PreAuthorize.class))
                .toList();

        assertThat(annotated).as("%s should have at least one @PreAuthorize method", cp.controller().getSimpleName())
                .isNotEmpty();

        for (Method method : annotated) {
            String expression = method.getAnnotation(PreAuthorize.class).value();
            assertThat(expression)
                    .as("%s#%s @PreAuthorize expression", cp.controller().getSimpleName(), method.getName())
                    .isEqualTo("hasAuthority('" + cp.permissionCode() + "')");
        }
    }

    @Test
    void everyMappedController_isCoveredByThisTest() {
        // Guards against silently dropping a controller from controllerPermissions() above.
        assertThat(controllerPermissions().count()).isEqualTo(13);
    }
}
