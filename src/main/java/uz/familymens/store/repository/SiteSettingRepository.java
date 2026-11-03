package uz.familymens.store.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.familymens.store.model.SiteSetting;

public interface SiteSettingRepository extends JpaRepository<SiteSetting, String> {
}
