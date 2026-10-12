package com.hcmut.emobility.m3vehicle;
import com.hcmut.emobility.m3vehicle.controller.VehicleController;
import com.hcmut.emobility.m3vehicle.repository.RentalVehicleRepository;
import com.hcmut.emobility.m3vehicle.service.VehicleService;
import com.hcmut.emobility.shared.auth.*;
import com.hcmut.emobility.shared.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(VehicleController.class) @Import(SecurityConfig.class)
class VehicleIdentityTest {
    @Autowired MockMvc mvc; @MockBean VehicleService service;
    @MockBean RentalVehicleRepository vehicles; @MockBean AppUserRepository users;
    @Test @WithMockUser(username="student1",roles="STUDENT") void ignoresSpoofedHeaderAndUsesSessionIdentity() throws Exception {
        var user=new AppUser("student1","hash","Demo",UserRole.STUDENT);ReflectionTestUtils.setField(user,"id",7L);
        when(users.findByUsername("student1")).thenReturn(Optional.of(user));
        mvc.perform(post("/api/m3/vehicles/1/pick-up").header("X-User-Id","999")).andExpect(status().isOk());
        verify(service).pickUp(1L,7L);verify(service,never()).pickUp(1L,999L);
    }
}
