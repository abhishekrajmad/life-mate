package com.lifemate.expense.repository;

import com.lifemate.expense.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByUserIdAndNameIgnoreCase(String userId, String name);

    List<Category> findAllByUserIdOrderByNameAsc(String userId);

    Optional<Category> findByIdAndUserId(Long id, String userId);
}
