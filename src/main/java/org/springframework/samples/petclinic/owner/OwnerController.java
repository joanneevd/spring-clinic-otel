package org.springframework.samples.petclinic.owner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;

@Controller
class OwnerController {

	private static final String VIEWS_OWNER_CREATE_OR_UPDATE_FORM = "owners/createOrUpdateOwnerForm";

	private final OwnerRepository owners;

	private final Tracer tracer = GlobalOpenTelemetry.getTracer("petclinic-owner-controller");

	public OwnerController(OwnerRepository owners) {
		this.owners = owners;
	}

	@InitBinder
	public void setAllowedFields(WebDataBinder dataBinder) {
		dataBinder.setDisallowedFields("id");
	}

	@ModelAttribute("owner")
	public Owner findOwner(@PathVariable(name = "ownerId", required = false) Integer ownerId) {
		if (ownerId == null) {
			return new Owner();
		}
		return this.owners.findById(ownerId)
			.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
	}

	@GetMapping("/owners/new")
	public String initCreationForm() {
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/new")
	public String processCreationForm(@Valid Owner owner, BindingResult result, RedirectAttributes redirectAttributes) {

		Span span = tracer.spanBuilder("owner.create").startSpan();
		try (Scope scope = span.makeCurrent()) {

			if (result.hasErrors()) {
				return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
			}

			this.owners.save(owner);

			// ✅ AMAN: cek null dulu
			if (owner.getId() != null) {
				span.setAttribute("owner.id", owner.getId());
				return "redirect:/owners/" + owner.getId();
			}

			// ✅ fallback (sesuai behavior test Petclinic)
			return "redirect:/owners";

		}
		catch (Exception e) {
			span.recordException(e);
			throw e;
		}
		finally {
			span.end();
		}
	}

	@GetMapping("/owners/find")
	public String initFindForm() {
		return "owners/findOwners";
	}

	@GetMapping("/owners")
	public String processFindForm(@RequestParam(defaultValue = "1") int page, Owner owner, BindingResult result,
			Model model) {

		Span span = tracer.spanBuilder("owner.search").startSpan();
		try (Scope scope = span.makeCurrent()) {

			String lastName = owner.getLastName();
			if (lastName == null) {
				lastName = "";
			}

			Page<Owner> ownersResults = findPaginatedForOwnersLastName(page, lastName);

			if (ownersResults.isEmpty()) {
				result.rejectValue("lastName", "notFound", "not found");
				return "owners/findOwners";
			}

			if (ownersResults.getTotalElements() == 1) {
				Owner foundOwner = ownersResults.iterator().next();
				span.setAttribute("owner.id", foundOwner.getId());
				return "redirect:/owners/" + foundOwner.getId();
			}

			return addPaginationModel(page, model, ownersResults);

		}
		catch (Exception e) {
			span.recordException(e);
			throw e;
		}
		finally {
			span.end();
		}
	}

	private String addPaginationModel(int page, Model model, Page<Owner> paginated) {
		List<Owner> listOwners = paginated.getContent();
		model.addAttribute("currentPage", page);
		model.addAttribute("totalPages", paginated.getTotalPages());
		model.addAttribute("totalItems", paginated.getTotalElements());
		model.addAttribute("listOwners", listOwners);
		return "owners/ownersList";
	}

	private Page<Owner> findPaginatedForOwnersLastName(int page, String lastname) {
		int pageSize = 5;
		Pageable pageable = PageRequest.of(page - 1, pageSize);
		return owners.findByLastNameStartingWith(lastname, pageable);
	}

	@GetMapping("/owners/{ownerId}/edit")
	public String initUpdateOwnerForm(@PathVariable("ownerId") int ownerId) {
		return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
	}

	@PostMapping("/owners/{ownerId}/edit")
	public String processUpdateOwnerForm(@Valid Owner owner, BindingResult result, @PathVariable("ownerId") int ownerId,
			RedirectAttributes redirectAttributes) {

		Span span = tracer.spanBuilder("owner.update").startSpan();
		try (Scope scope = span.makeCurrent()) {

			if (result.hasErrors()) {
				redirectAttributes.addFlashAttribute("error", "There was an error in updating the owner.");
				return VIEWS_OWNER_CREATE_OR_UPDATE_FORM;
			}

			if (!Objects.equals(owner.getId(), ownerId)) {
				result.rejectValue("id", "mismatch", "The owner ID in the form does not match the URL.");
				redirectAttributes.addFlashAttribute("error", "Owner ID mismatch. Please try again.");
				return "redirect:/owners/{ownerId}/edit";
			}

			owner.setId(ownerId);
			this.owners.save(owner);
			span.setAttribute("owner.id", ownerId);
			return "redirect:/owners/{ownerId}";

		}
		catch (Exception e) {
			span.recordException(e);
			throw e;
		}
		finally {
			span.end();
		}
	}

	@GetMapping("/owners/{ownerId}")
	public ModelAndView showOwner(@PathVariable("ownerId") int ownerId) {

		Span span = tracer.spanBuilder("owner.get").startSpan();
		try (Scope scope = span.makeCurrent()) {

			ModelAndView mav = new ModelAndView("owners/ownerDetails");
			Optional<Owner> optionalOwner = this.owners.findById(ownerId);
			Owner owner = optionalOwner
				.orElseThrow(() -> new IllegalArgumentException("Owner not found with id: " + ownerId));
			mav.addObject(owner);
			span.setAttribute("owner.id", ownerId);
			return mav;

		}
		catch (Exception e) {
			span.recordException(e);
			throw e;
		}
		finally {
			span.end();
		}
	}

}
