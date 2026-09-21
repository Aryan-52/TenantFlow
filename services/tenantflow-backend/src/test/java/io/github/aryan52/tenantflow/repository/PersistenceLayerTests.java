package io.github.aryan52.tenantflow.repository;

import io.github.aryan52.tenantflow.entity.Membership;
import io.github.aryan52.tenantflow.entity.Role;
import io.github.aryan52.tenantflow.entity.Tenant;
import io.github.aryan52.tenantflow.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Optional;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:tenantflow_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "tenantflow.jwt.secret=test-context-secret-test-context-secret",
    "tenantflow.jwt.expiration-seconds=3600"
})
class PersistenceLayerTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private MembershipRepository membershipRepository;

    @Test
    void testEntitiesAndRepositories() {
        // Create User
        User user = User.builder()
            .name("Test User")
            .email("test@example.com")
            .passwordHash("hash")
            .build();
        user = userRepository.save(user);
        assertThat(user.getId()).isNotNull();

        // Create Tenant
        Tenant tenant = Tenant.builder()
            .name("Test Tenant")
            .slug("test-tenant")
            .build();
        tenant = tenantRepository.save(tenant);
        assertThat(tenant.getId()).isNotNull();

        // Create Membership
        Membership membership = Membership.builder()
            .user(user)
            .tenant(tenant)
            .role(Role.OWNER)
            .build();
        membership = membershipRepository.save(membership);
        assertThat(membership.getId()).isNotNull();

        // Fetch User by Email
        Optional<User> fetchedUser = userRepository.findByEmail("test@example.com");
        assertThat(fetchedUser).isPresent();
        assertThat(fetchedUser.get().getName()).isEqualTo("Test User");

        // Fetch Tenant by Slug
        Optional<Tenant> fetchedTenant = tenantRepository.findBySlug("test-tenant");
        assertThat(fetchedTenant).isPresent();
        assertThat(fetchedTenant.get().getName()).isEqualTo("Test Tenant");

        // Fetch Membership
        Optional<Membership> fetchedMembership = membershipRepository.findByUser_IdAndTenant_Id(user.getId(), tenant.getId());
        assertThat(fetchedMembership).isPresent();
        assertThat(fetchedMembership.get().getRole()).isEqualTo(Role.OWNER);
        
        // Clean up
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }
}
