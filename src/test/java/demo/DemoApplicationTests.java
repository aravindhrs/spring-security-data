package demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.repository.augment.QueryAugmentor;
import org.springframework.data.repository.augment.QueryContext;
import org.springframework.data.repository.augment.UpdateContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = DemoApplication.class)
@Transactional
public class DemoApplicationTests {
	@Autowired
	EntityManager entityManager;
	
	@Autowired
	MyDomainRepository unaugmentedRepository;
	
	MyDomainRepository repository;
	
	
	@Before
	public void setup() {
		repository = createAugmentedFactory(MyDomainRepository.class, new AclQueryAugmentor<Object>());
	}
	
	// save(MyDomain)
	
	@WithMockUser("rob")
	@Test
	public void saveCreateSuccess() {
		MyDomain toSave = new MyDomain();
		toSave.setAttribute("saveCreateSuccess");

		MyDomain saved = repository.save(toSave);
		assertThat(unaugmentedRepository.findOne(saved.getId())).isNotNull();
	}

	/**
	 * No way to intercept a Save since SimpleJpaRepository does not use
	 * QueryExecutor
	 */
	@WithMockUser("luke")
	@Test
	public void saveCreateNoPermission() {

		long before = unaugmentedRepository.count();

		MyDomain toSave = new MyDomain();
		toSave.setAttribute("saveCreateNoPermission");

		// TODO not sure how this should fail saved == null || just null id || exception
		repository.save(toSave);

		assertThat(unaugmentedRepository.count()).isEqualTo(before);
		assertThat(toSave.getId()).isNull();
	}

	@WithMockUser("rob")
	@Test
	public void saveUpdateNoPermission() {
		MyDomain toUpdate = repository.findOne(2L);

		toUpdate.setAttribute("saveUpdateNoPermission");

		repository.save(toUpdate);

		assertThat(unaugmentedRepository.getOne(toUpdate.getId()).getAttribute()).isNotEqualTo("saveUpdateNoPermission");
	}

	// TODO save (create new, then update, then delete)

	// saveAndFlush(MyDomain)

	@WithMockUser("rob")
	@Test
	public void saveAndFlushUpdateNoPermission() {
		MyDomain toUpdate = repository.findOne(2L);

		toUpdate.setAttribute("saveUpdateNoPermission");

		repository.saveAndFlush(toUpdate);

		assertThat(unaugmentedRepository.getOne(toUpdate.getId()).getAttribute()).isNotEqualTo("saveUpdateNoPermission");
	}
	
	// save(Iterable<MyDomain>)

	@WithMockUser("rob")
	@Test
	public void saveIterableNoPermission() {
		MyDomain toUpdate = repository.findOne(2L);

		toUpdate.setAttribute("saveUpdateNoPermission");

		repository.save(Arrays.asList(toUpdate));

		assertThat(unaugmentedRepository.getOne(toUpdate.getId()).getAttribute()).isNotEqualTo("saveUpdateNoPermission");
	}

	// findOne(Long)
	
	@WithMockUser("rob")
	@Test
	public void findOneSuccess() {
		assertThat(repository.findOne(1L)).isNotNull();
	}

	@WithMockUser("luke")
	@Test
	public void findOneNoPermission() {
		assertThat(repository.findOne(1L)).isNull();
	}
	
	// Bug in Spring Data...why noop augmentor produces NonUniqueResultsException
	@Test
	public void findOneNoOpAugmentor() {
		repository = noopAugmentedRepository();
		
		assertThat(repository.findOne(1L)).isNotNull();
	}

	// exists()

	@WithMockUser("rob")
	@Test
	public void existsSuccess() {
		assertThat(repository.exists(1L)).isTrue();
	}

	@WithMockUser("luke")
	@Test
	public void existsNoPermission() {
		assertThat(repository.exists(1L)).isFalse();
	}

	// findAll()

	@WithMockUser("rob")
	@Test
	public void findAllRobFindsAllowed() {
		Iterable<MyDomain> results = repository.findAll();

		assertThat(results).hasSize(2);
	}

	@WithMockUser("luke")
	@Test
	public void findAllLukeDoesNotFindRobs() {
		Iterable<MyDomain> results = repository.findAll();

		assertThat(results).isEmpty();
	}
	
	// findAll(Iterable)

	@WithMockUser("rob")
	@Test
	public void findAllIterableRobFindsAllowed() {
		Iterable<MyDomain> results = repository.findAll(Arrays.asList(1L,2L));

		assertThat(results).hasSize(2);
	}

	@WithMockUser("luke")
	@Test
	public void findAllIterableLukeDoesNotFindRobs() {
		Iterable<MyDomain> results = repository.findAll(Arrays.asList(1L,2L));

		assertThat(results).isEmpty();
	}
	
	// count()
	
	@WithMockUser("rob")
	@Test
	public void countRobFindsAllowed() {
		long results = repository.count();

		assertThat(results).isEqualTo(2);
	}

	@WithMockUser("luke")
	@Test
	public void countLukeDoesNotFindRobs() {
		long results = repository.count();

		assertThat(results).isEqualTo(0);
	}
	
	// delete(Long)
	
	/**
	 * Has read and write access
	 */
	@WithMockUser("rob")
	@Test
	public void deleteLongRobSuccess() {
		long id = 1L;
		
		repository.delete(id);

		assertThat(unaugmentedRepository.findOne(id)).isNull();
	}

	/**
	 * Has only read access, so write should fail
	 */
	@WithMockUser("rob")
	@Test
	public void deleteLongRobFail() {
		long id = 2L;
		
		repository.delete(id);

		assertThat(unaugmentedRepository.findOne(id)).isNotNull();
	}

	/**
	 * This should behave the same as the standard repository, but fails w/
	 * NonUniqueResultsException
	 */
	@Test
	public void deleteLongNoOpAugmentor() {
		repository = noopAugmentedRepository();
		long id = 1L;
		
		repository.delete(id);

		assertThat(unaugmentedRepository.findOne(id)).isNull();
	}
	
	// delete(MyDomain)
	
	/**
	 * Has read and write access
	 */
	@WithMockUser("rob")
	@Test
	public void deleteMyDomainRobSuccess() {
		MyDomain toDelete = unaugmentedRepository.findOne(1L);
		
		repository.delete(toDelete);

		assertThat(unaugmentedRepository.findOne(toDelete.getId())).isNull();
	}

	/**
	 * Has only read access, so write should fail
	 */
	@WithMockUser("rob")
	@Test
	public void deleteMyDomainRobFail() {
		MyDomain toDelete = unaugmentedRepository.findOne(2L);
		
		repository.delete(toDelete);

		assertThat(unaugmentedRepository.findOne(toDelete.getId())).isNotNull();
	}
	
	// delete(Iterable<MyComain>)

	/**
	 * Has read and write access
	 */
	@WithMockUser("rob")
	@Test
	public void deleteIterableMyDomainRobSuccess() {
		MyDomain toDelete = unaugmentedRepository.findOne(1L);
		
		repository.delete(Arrays.asList(toDelete));

		assertThat(unaugmentedRepository.findOne(toDelete.getId())).isNull();
	}

	/**
	 * Has only read access, so write should fail
	 */
	@WithMockUser("rob")
	@Test
	public void deleteIterableMyDomainRobFail() {
		MyDomain toDelete = unaugmentedRepository.findOne(2L);
		
		repository.delete(Arrays.asList(toDelete));

		assertThat(unaugmentedRepository.findOne(toDelete.getId())).isNotNull();
	}
	
	// deleteAll()	

	/**
	 * Has read and write access
	 */
	@WithMockUser("rob")
	@Test
	public void deleteAllOnlyWriteable() {
		long size = unaugmentedRepository.count();
		
		repository.deleteAll();

		assertThat(unaugmentedRepository.count()).isEqualTo(size - 1);
	}
	
	// TODO findAll(Sort)

	@WithMockUser("rob")
	@Test
	public void findAllSortRobOnlyFindsAllowed() {
		List<MyDomain> results = repository.findAll(new Sort("id"));

		assertThat(results).hasSize(2);
	}

	@WithMockUser("luke")
	@Test
	public void findAllSortLukeOnlyFindsAllowed() {
		List<MyDomain> results = repository.findAll(new Sort("id"));

		assertThat(results).isEmpty();
	}

	// findAll(Pageable)

	@WithMockUser("rob")
	@Test
	public void findAllPageableRobOnlyFindsAllowed() {
		Page<MyDomain> results = repository.findAll(new PageRequest(0, 10));

		assertThat(results.getTotalElements()).isEqualTo(2);
	}

	@WithMockUser("luke")
	@Test
	public void findAllPageableLukeOnlyFindsAllowed() {
		Page<MyDomain> results = repository.findAll(new PageRequest(0, 10));

		assertThat(results.getTotalElements()).isEqualTo(0);
	}
	
	// findAllWithQuery (demos a bug when @Query present)
	
	@WithMockUser("rob")
	@Test
	public void findAllWithQueryOnlyRobFindsAllowed() {
		List<MyDomain> results = repository.findAllWithQuery();

		assertThat(results.size()).isEqualTo(2);
	}

	@WithMockUser("luke")
	@Test
	public void findAllWithQueryLukeFindNone() {
		List<MyDomain> results = repository.findAllWithQuery();

		assertThat(results).isEmpty();
	}
	
	/**
	 * Test for SQL Injection attack. This is only one vector and we should be
	 * careful to use built in mechanisms for escaping.
	 */
	@WithMockUser("user'")
	@Test
	public void findAllWithQueryUsernameContainSingleQuote() {
		List<MyDomain> results = repository.findAllWithQuery();

		assertThat(results).isEmpty();
	}

	// TODO test modifying a cached object and flushing

	// TODO Superclass
	
	// TODO Associations

	// --- helpers
	
	@SafeVarargs
	private final <T> T createAugmentedFactory(Class<T> repository, QueryAugmentor<? extends QueryContext<?>, ? extends QueryContext<?>, ? extends UpdateContext<?>>... augmentors) {
		JpaRepositoryFactory factory = new JpaRepositoryFactory(entityManager);
		if(augmentors != null) {
			factory.setQueryAugmentors(Arrays.asList(augmentors));
		}
		
		return (T) factory.getRepository(repository);
	}
	
	/**
	 * Sometimes adding a no op Augmentor breaks things 
	 * @return
	 */
	private MyDomainRepository noopAugmentedRepository() {
		return createAugmentedFactory(MyDomainRepository.class, new NoOpAugmentor());
	}
}
